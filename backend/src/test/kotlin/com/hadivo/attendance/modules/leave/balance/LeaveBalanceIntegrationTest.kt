package com.hadivo.attendance.modules.leave.balance

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.audit.AuditLogRepository
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.calendar.HolidayType
import com.hadivo.attendance.modules.calendar.TenantHoliday
import com.hadivo.attendance.modules.calendar.TenantHolidayRepository
import com.hadivo.attendance.modules.leave.LeaveRequest
import com.hadivo.attendance.modules.leave.LeaveRequestRepository
import com.hadivo.attendance.modules.leave.LeaveRequestStatus
import com.hadivo.attendance.modules.leave.LeaveRequestType
import com.hadivo.attendance.modules.membership.Membership
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.settings.TenantAttendanceSettings
import com.hadivo.attendance.modules.settings.TenantAttendanceSettingsRepository
import com.hadivo.attendance.modules.subscription.Subscription
import com.hadivo.attendance.modules.subscription.SubscriptionPlan
import com.hadivo.attendance.modules.subscription.SubscriptionRepository
import com.hadivo.attendance.modules.subscription.SubscriptionStatus
import com.hadivo.attendance.modules.tenant.Tenant
import com.hadivo.attendance.modules.tenant.TenantMode
import com.hadivo.attendance.modules.tenant.TenantRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveBalanceIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var leaves: LeaveRequestRepository
    @Autowired private lateinit var balances: LeaveBalanceRepository
    @Autowired private lateinit var ledgers: LeaveBalanceLedgerRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository
    @Autowired private lateinit var balanceService: LeaveBalanceService
    @Autowired private lateinit var tenantHolidays: TenantHolidayRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `tenant admin can get default leave policy created on demand`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/leave-policy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.annualLeaveQuotaDays").value(12.00))
            .andExpect(jsonPath("$.data.sickLeaveRequiresBalance").value(false))
            .andExpect(jsonPath("$.data.active").value(true))

        assertThat(policies.findByTenantId(admin.tenantId)).isNotNull
    }

    @Test
    fun `tenant admin can update leave policy with audit`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            put("/api/v1/tenants/${admin.tenantId}/leave-policy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("annualLeaveQuotaDays" to 15, "sickLeaveRequiresBalance" to true),
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.annualLeaveQuotaDays").value(15.00))
            .andExpect(jsonPath("$.data.sickLeaveRequiresBalance").value(true))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("LEAVE_POLICY_UPDATED")
    }

    @Test
    fun `employee cannot update leave policy`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        mvc.perform(
            put("/api/v1/tenants/${ctx.tenantId}/leave-policy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("annualLeaveQuotaDays" to 20)))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `update policy rejects quota above max`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            put("/api/v1/tenants/${admin.tenantId}/leave-policy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("annualLeaveQuotaDays" to 400)))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `cross tenant policy access rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/leave-policy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `balance auto initialized from policy on first read with INITIAL ledger`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val year = LocalDate.now().year

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/leave-balance")
                .param("year", year.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.annualQuotaDays").value(12.00))
            .andExpect(jsonPath("$.data.usedDays").value(0.00))
            .andExpect(jsonPath("$.data.remainingDays").value(12.00))

        val ledger = ledgers.findAllByTenantIdAndUserIdAndYearOrderByCreatedAtDesc(
            admin.tenantId, employee.userId, year,
        )
        assertThat(ledger).hasSize(1)
        assertThat(ledger[0].changeType).isEqualTo(LeaveBalanceChangeType.INITIAL)
        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("LEAVE_BALANCE_INITIALIZED")
    }

    @Test
    fun `employee can read own balance`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/members/${ctx.userId}/leave-balance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value(ctx.userId.toString()))
    }

    @Test
    fun `employee cannot read another user balance`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employeeA = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeB = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val tokenA = jwt.issueAccessToken(employeeA.userId, employeeA.email).token

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/members/${employeeB.userId}/leave-balance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tokenA")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `tenant admin can adjust balance with note and ledger`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val year = LocalDate.now().year

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/leave-balance/adjust")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("year" to year, "days" to 2, "note" to "Bonus tahunan"),
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.adjustedDays").value(2.00))
            .andExpect(jsonPath("$.data.remainingDays").value(14.00))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("LEAVE_BALANCE_ADJUSTED")

        val ledger = ledgers.findAllByTenantIdAndUserIdAndYearOrderByCreatedAtDesc(
            admin.tenantId, employee.userId, year,
        )
        assertThat(ledger.map { it.changeType }).contains(LeaveBalanceChangeType.ADJUST)
    }

    @Test
    fun `adjust requires note`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val year = LocalDate.now().year

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/leave-balance/adjust")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("year" to year, "days" to 1)))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `adjust rejected when result would be negative`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val year = LocalDate.now().year

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/leave-balance/adjust")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("year" to year, "days" to -20, "note" to "Test negatif"),
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `cross tenant balance access rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)
        val employeeB = seedUserInTenant(tenantB.tenantId, Role.EMPLOYEE)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/members/${employeeB.userId}/leave-balance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `annual leave approval deducts balance and writes DEDUCT ledger`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        // Use mid-year fixed offset to keep range inside one calendar year regardless of run date.
        val today = LocalDate.of(LocalDate.now().year, 6, 1)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = today,
                endDate = today.plusDays(2),
                reason = "Liburan",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))

        val year = today.year
        val balance = balances.findByTenantIdAndUserIdAndYear(admin.tenantId, employee.userId, year)
        assertThat(balance).isNotNull
        assertThat(balance!!.usedDays).isEqualByComparingTo(BigDecimal("3.00"))
        assertThat(balance.remainingDays).isEqualByComparingTo(BigDecimal("9.00"))

        val deductLedgers = ledgers.findAllByTenantIdAndUserIdAndYearOrderByCreatedAtDesc(
            admin.tenantId, employee.userId, year,
        ).filter { it.changeType == LeaveBalanceChangeType.DEDUCT }
        assertThat(deductLedgers).hasSize(1)
        assertThat(deductLedgers[0].leaveRequestId).isEqualTo(leave.id)
        assertThat(deductLedgers[0].daysChanged).isEqualByComparingTo(BigDecimal("3.00"))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("LEAVE_BALANCE_DEDUCTED")
    }

    @Test
    fun `annual leave approval fails when balance insufficient and request stays pending`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val today = LocalDate.of(LocalDate.now().year, 6, 1)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = today,
                endDate = today.plusDays(20),
                reason = "Liburan panjang",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

        val refreshed = leaves.findById(leave.id!!).orElseThrow()
        assertThat(refreshed.status).isEqualTo(LeaveRequestStatus.PENDING)
    }

    @Test
    fun `annual leave cross year request is rejected`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = LocalDate.of(2026, 12, 30),
                endDate = LocalDate.of(2027, 1, 2),
                reason = "Liburan tahun baru",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

        val refreshed = leaves.findById(leave.id!!).orElseThrow()
        assertThat(refreshed.status).isEqualTo(LeaveRequestStatus.PENDING)
    }

    @Test
    fun `sick leave approval does not deduct balance by default`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val today = LocalDate.of(LocalDate.now().year, 6, 1)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.SICK,
                startDate = today,
                endDate = today.plusDays(1),
                reason = "Demam",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))

        val balance = balances.findByTenantIdAndUserIdAndYear(admin.tenantId, employee.userId, today.year)
        if (balance != null) {
            assertThat(balance.usedDays).isEqualByComparingTo(BigDecimal.ZERO)
        }
        val deduct = auditLogs.findAll().filter {
            it.tenantId == admin.tenantId && it.action == "LEAVE_BALANCE_DEDUCTED"
        }
        assertThat(deduct).isEmpty()
    }

    @Test
    fun `attendance correction approval does not deduct balance`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val today = LocalDate.of(LocalDate.now().year, 6, 1)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = today,
                endDate = today,
                reason = null,
                requestedClockInAt = Instant.parse("2026-01-01T01:00:00Z"),
                requestedClockOutAt = Instant.parse("2026-01-01T10:00:00Z"),
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)

        val deduct = auditLogs.findAll().filter {
            it.tenantId == admin.tenantId && it.action == "LEAVE_BALANCE_DEDUCTED"
        }
        assertThat(deduct).isEmpty()
    }

    @Test
    fun `employee cannot adjust balance`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        val year = LocalDate.now().year

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/members/${ctx.userId}/leave-balance/adjust")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("year" to year, "days" to 1, "note" to "Test"),
                    )
                )
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deductForApproval is idempotent at service level`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val today = LocalDate.of(LocalDate.now().year, 6, 1)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = today,
                endDate = today.plusDays(1),
                reason = "Liburan singkat",
            )
        )

        balanceService.deductForApproval(leave, admin.userId)
        balanceService.deductForApproval(leave, admin.userId)

        val balance = balances.findByTenantIdAndUserIdAndYear(admin.tenantId, employee.userId, today.year)
        assertThat(balance).isNotNull
        assertThat(balance!!.usedDays).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(balance.remainingDays).isEqualByComparingTo(BigDecimal("10.00"))

        val deductLedgers = ledgers.findAllByTenantIdAndUserIdAndYearOrderByCreatedAtDesc(
            admin.tenantId, employee.userId, today.year,
        ).filter { it.changeType == LeaveBalanceChangeType.DEDUCT && it.leaveRequestId == leave.id }
        assertThat(deductLedgers).hasSize(1)
    }

    @Test
    fun `balance ledger history captures initial adjust and deduct change types`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val today = LocalDate.of(LocalDate.now().year, 6, 1)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/leave-balance")
                .param("year", today.year.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/leave-balance/adjust")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("year" to today.year, "days" to 1, "note" to "Bonus"),
                    )
                )
        )
            .andExpect(status().isOk)

        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = today,
                endDate = today,
                reason = "Cuti satu hari",
            )
        )
        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)

        val history = ledgers.findAllByTenantIdAndUserIdAndYearOrderByCreatedAtDesc(
            admin.tenantId, employee.userId, today.year,
        )
        val types = history.map { it.changeType }.toSet()
        assertThat(types).containsExactlyInAnyOrder(
            LeaveBalanceChangeType.INITIAL,
            LeaveBalanceChangeType.ADJUST,
            LeaveBalanceChangeType.DEDUCT,
        )
    }

    @Test
    fun `annual leave Fri to Mon deducts workday count not calendar count`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        // 2026-06-05 Fri, 2026-06-08 Mon → 4 calendar days, 2 workdays.
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = LocalDate.of(2026, 6, 5),
                endDate = LocalDate.of(2026, 6, 8),
                reason = "Liburan weekend panjang",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)

        val balance = balances.findByTenantIdAndUserIdAndYear(admin.tenantId, employee.userId, 2026)
        assertThat(balance).isNotNull
        assertThat(balance!!.usedDays).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(balance.remainingDays).isEqualByComparingTo(BigDecimal("10.00"))
    }

    @Test
    fun `annual leave over only non workdays is rejected and stays pending`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        // 2026-06-06 Sat, 2026-06-07 Sun → 0 workdays under default Mon-Fri policy.
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = LocalDate.of(2026, 6, 6),
                endDate = LocalDate.of(2026, 6, 7),
                reason = "Cuti weekend saja",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))

        val refreshed = leaves.findById(leave.id!!).orElseThrow()
        assertThat(refreshed.status).isEqualTo(LeaveRequestStatus.PENDING)
    }

    @Test
    fun `holiday in range reduces workday deduction`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        tenantHolidays.save(
            TenantHoliday(
                tenantId = admin.tenantId,
                holidayDate = LocalDate.of(2026, 6, 9),
                name = "Hari Libur Internal",
                type = HolidayType.COMPANY,
                active = true,
            )
        )
        // 2026-06-08 Mon, 06-09 Tue (holiday), 06-10 Wed → 3 weekdays, 2 workdays.
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ANNUAL_LEAVE,
                startDate = LocalDate.of(2026, 6, 8),
                endDate = LocalDate.of(2026, 6, 10),
                reason = "Pakai libur internal",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)

        val balance = balances.findByTenantIdAndUserIdAndYear(admin.tenantId, employee.userId, 2026)
        assertThat(balance!!.usedDays).isEqualByComparingTo(BigDecimal("2.00"))
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Balance Test Tenant",
                slug = "balance-${UUID.randomUUID().toString().take(8)}",
                mode = TenantMode.COMPANY,
            )
        )
        val tenantId = tenant.id!!
        subscriptions.save(
            Subscription(
                tenantId = tenantId,
                plan = SubscriptionPlan.FREE,
                maxMembers = SubscriptionPlan.FREE.maxMembers,
                startedAt = Instant.now(),
                status = SubscriptionStatus.ACTIVE,
            )
        )
        settings.save(TenantAttendanceSettings(tenantId = tenantId))
        val user = seedUserInTenant(tenantId, role)
        val token = jwt.issueAccessToken(user.userId, user.email).token
        return TestContext(tenantId = tenantId, userId = user.userId, email = user.email, token = token)
    }

    private fun seedUserInTenant(tenantId: UUID, role: Role): TestUser {
        val user = users.save(
            User(
                email = "balance-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Balance Test User",
            )
        )
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
        return TestUser(userId = user.id!!, email = user.email)
    }

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val email: String,
        val token: String,
    )

    private data class TestUser(
        val userId: UUID,
        val email: String,
    )
}
