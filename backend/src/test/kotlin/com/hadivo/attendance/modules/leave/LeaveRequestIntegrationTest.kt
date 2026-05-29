package com.hadivo.attendance.modules.leave

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceRecord
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.audit.AuditLogRepository
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.membership.Membership
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.notification.NotificationRequest
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
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doThrow
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.containsString
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveRequestIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var records: AttendanceRecordRepository
    @Autowired private lateinit var leaves: LeaveRequestRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `employee can create sick leave request`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        val response = mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "requestType" to "SICK",
                            "startDate" to "2026-02-01",
                            "endDate" to "2026-02-02",
                            "reason" to "Demam",
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.requestType").value("SICK"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .response

        val data = mapper.readTree(response.contentAsString).get("data")
        assertThat(data.get("requesterUserId").asText()).isEqualTo(ctx.userId.toString())
        assertThat(auditLogs.findAll().filter { it.tenantId == ctx.tenantId }.map { it.action })
            .contains("LEAVE_REQUEST_CREATED")
    }

    @Test
    fun `employee cannot specify another requester through leave endpoint`() {
        // requester is always derived from principal; employees only get their own
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        val other = seedUserInTenant(ctx.tenantId, Role.EMPLOYEE)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = ctx.tenantId,
                requesterUserId = other.userId,
                requestType = LeaveRequestType.PERMISSION,
                startDate = LocalDate.of(2026, 2, 1),
                endDate = LocalDate.of(2026, 2, 1),
                reason = "Other user",
            )
        )

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/leave-requests/${leave.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `tenant admin can list all tenant requests while employee only sees own`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        leaves.save(leaveOf(admin.tenantId, admin.userId, LeaveRequestType.SICK))
        leaves.save(leaveOf(admin.tenantId, employee.userId, LeaveRequestType.PERMISSION))

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].requesterUserId").value(employee.userId.toString()))
    }

    @Test
    fun `cross tenant access is rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `tenant admin can approve pending request`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val leave = leaves.save(leaveOf(admin.tenantId, employee.userId, LeaveRequestType.PERMISSION))

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "Disetujui")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.reviewerUserId").value(admin.userId.toString()))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("LEAVE_REQUEST_APPROVED")
    }

    @Test
    fun `tenant admin can reject pending request`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val leave = leaves.save(leaveOf(admin.tenantId, employee.userId, LeaveRequestType.SICK))

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "Tidak ada bukti")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
    }

    @Test
    fun `requester can cancel pending request`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token
        val leave = leaves.save(leaveOf(admin.tenantId, employee.userId, LeaveRequestType.PERMISSION))

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("LEAVE_REQUEST_CANCELLED")
    }

    @Test
    fun `cancel rejects approved request`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token
        val leave = leaves.save(
            leaveOf(admin.tenantId, employee.userId, LeaveRequestType.PERMISSION).apply {
                status = LeaveRequestStatus.APPROVED
            }
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `invalid date range is rejected`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "requestType" to "SICK",
                            "startDate" to "2026-02-10",
                            "endDate" to "2026-02-01",
                            "reason" to "Demam",
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `overlap pending request is rejected`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        leaves.save(
            LeaveRequest(
                tenantId = ctx.tenantId,
                requesterUserId = ctx.userId,
                requestType = LeaveRequestType.PERMISSION,
                startDate = LocalDate.of(2026, 2, 1),
                endDate = LocalDate.of(2026, 2, 3),
                reason = "Acara keluarga",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "requestType" to "SICK",
                            "startDate" to "2026-02-02",
                            "endDate" to "2026-02-05",
                            "reason" to "Sakit",
                        )
                    )
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `attendance correction requires clock in or out time`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "requestType" to "ATTENDANCE_CORRECTION",
                            "startDate" to "2026-02-02",
                            "endDate" to "2026-02-02",
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `approved leave appears in daily report row and totals`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE, fullName = "Sick Employee")
        val date = LocalDate.of(2026, 3, 4)
        leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.SICK,
                startDate = date,
                endDate = date,
                reason = "Demam tinggi",
                status = LeaveRequestStatus.APPROVED,
            )
        )

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/reports/attendance/daily")
                .param("date", date.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.leaveTotals.SICK").value(1))
            .andExpect(jsonPath("$.data.rows[0].leaveType").value("SICK"))
            .andExpect(jsonPath("$.data.rows[0].leaveStatus").value("APPROVED"))
    }

    @Test
    fun `export csv contains leave columns and leave only rows`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE, fullName = "Permission Employee")
        val date = LocalDate.of(2026, 4, 1)

        records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = Instant.parse("2026-04-01T01:00:00Z"),
                status = AttendanceStatus.ON_TIME,
            )
        )
        leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.PERMISSION,
                startDate = date.plusDays(1),
                endDate = date.plusDays(1),
                reason = "Urusan keluarga",
                status = LeaveRequestStatus.APPROVED,
            )
        )

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/reports/attendance/export.csv")
                .param("from", date.toString())
                .param("to", date.plusDays(1).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Leave Type,Leave Status")))
            .andExpect(content().string(containsString("PERMISSION,APPROVED")))
            .andExpect(content().string(containsString("ON_TIME")))
    }

    @Test
    fun `notification publisher failure does not break approval`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val leave = leaves.save(leaveOf(admin.tenantId, employee.userId, LeaveRequestType.PERMISSION))

        doThrow(IllegalStateException("RabbitMQ unavailable"))
            .`when`(rabbit)
            .convertAndSend(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(NotificationRequest::class.java),
            )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/leave-requests/${leave.id}/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
    }

    private fun leaveOf(tenantId: UUID, userId: UUID, type: LeaveRequestType): LeaveRequest =
        LeaveRequest(
            tenantId = tenantId,
            requesterUserId = userId,
            requestType = type,
            startDate = LocalDate.of(2026, 2, 1),
            endDate = LocalDate.of(2026, 2, 1),
            reason = "seed",
        )

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Leave Test Tenant",
                slug = "leave-${UUID.randomUUID().toString().take(8)}",
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

    private fun seedUserInTenant(tenantId: UUID, role: Role, fullName: String = "Leave Test User"): TestUser {
        val user = users.save(
            User(
                email = "leave-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = fullName,
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
