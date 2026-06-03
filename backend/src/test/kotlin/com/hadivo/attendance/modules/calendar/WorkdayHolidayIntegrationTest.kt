package com.hadivo.attendance.modules.calendar

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.audit.AuditLogRepository
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkdayHolidayIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var holidays: TenantHolidayRepository
    @Autowired private lateinit var workdaySettings: TenantWorkdaySettingsRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository
    @Autowired private lateinit var workdayCalendar: WorkdayCalendarService

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `tenant admin gets default workday settings Mon to Fri`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.mondayWorkday").value(true))
            .andExpect(jsonPath("$.data.fridayWorkday").value(true))
            .andExpect(jsonPath("$.data.saturdayWorkday").value(false))
            .andExpect(jsonPath("$.data.sundayWorkday").value(false))
    }

    @Test
    fun `tenant admin can update workday settings with audit`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            put("/api/v1/tenants/${admin.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("saturdayWorkday" to true)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.saturdayWorkday").value(true))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("WORKDAY_SETTINGS_UPDATED")
    }

    @Test
    fun `employee cannot update workday settings`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        mvc.perform(
            put("/api/v1/tenants/${ctx.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("saturdayWorkday" to true)))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `cannot set all days as non workday`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            put("/api/v1/tenants/${admin.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "mondayWorkday" to false,
                            "tuesdayWorkday" to false,
                            "wednesdayWorkday" to false,
                            "thursdayWorkday" to false,
                            "fridayWorkday" to false,
                            "saturdayWorkday" to false,
                            "sundayWorkday" to false,
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `cross tenant workday access rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `tenant admin can create holiday with audit`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "holidayDate" to "2026-06-10",
                            "name" to "Cuti Bersama",
                            "type" to "COMPANY",
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.holidayDate").value("2026-06-10"))
            .andExpect(jsonPath("$.data.type").value("COMPANY"))
            .andExpect(jsonPath("$.data.active").value(true))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("HOLIDAY_CREATED")
    }

    @Test
    fun `tenant admin can patch holiday active false with audit`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val saved = holidays.save(
            TenantHoliday(
                tenantId = admin.tenantId,
                holidayDate = LocalDate.of(2026, 6, 12),
                name = "Hari Libur Test",
                type = HolidayType.CUSTOM,
                active = true,
            )
        )

        mvc.perform(
            patch("/api/v1/tenants/${admin.tenantId}/holidays/${saved.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("active" to false)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.active").value(false))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("HOLIDAY_UPDATED")
    }

    @Test
    fun `employee cannot create or update holiday`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("holidayDate" to "2026-07-01", "name" to "Coba", "type" to "CUSTOM")
                    )
                )
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `cross tenant holiday access rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `list rejects from after to`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/holidays")
                .param("from", "2026-07-01")
                .param("to", "2026-06-01")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `duplicate holiday is rejected with conflict`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val payload = mapOf("holidayDate" to "2026-08-17", "name" to "Hari Kemerdekaan", "type" to "NATIONAL")

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `workday calculation excludes Saturday and Sunday by default`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        // 2026-06-05 = Friday, 2026-06-08 = Monday → 4 calendar days, 2 workdays.
        val workdays = workdayCalendar.countWorkdays(
            admin.tenantId,
            LocalDate.of(2026, 6, 5),
            LocalDate.of(2026, 6, 8),
        )
        assertThat(workdays).isEqualTo(2)
    }

    @Test
    fun `workday calculation includes Saturday when tenant enables Saturday`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        mvc.perform(
            put("/api/v1/tenants/${admin.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("saturdayWorkday" to true)))
        ).andExpect(status().isOk)

        // 2026-06-05 Fri → 06-08 Mon. Sat 06-06 now counted, Sun 06-07 still off → 3 workdays.
        val workdays = workdayCalendar.countWorkdays(
            admin.tenantId,
            LocalDate.of(2026, 6, 5),
            LocalDate.of(2026, 6, 8),
        )
        assertThat(workdays).isEqualTo(3)
    }

    @Test
    fun `workday calculation excludes active holiday`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        holidays.save(
            TenantHoliday(
                tenantId = admin.tenantId,
                holidayDate = LocalDate.of(2026, 6, 9),
                name = "Cuti Bersama",
                type = HolidayType.COMPANY,
                active = true,
            )
        )

        // 2026-06-08 Mon, 06-09 Tue (holiday), 06-10 Wed → 3 calendar weekdays, 2 workdays.
        val workdays = workdayCalendar.countWorkdays(
            admin.tenantId,
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 10),
        )
        assertThat(workdays).isEqualTo(2)
    }

    @Test
    fun `inactive holiday does not reduce workday count`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        holidays.save(
            TenantHoliday(
                tenantId = admin.tenantId,
                holidayDate = LocalDate.of(2026, 6, 9),
                name = "Cuti Tidak Aktif",
                type = HolidayType.CUSTOM,
                active = false,
            )
        )

        val workdays = workdayCalendar.countWorkdays(
            admin.tenantId,
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 10),
        )
        assertThat(workdays).isEqualTo(3)
    }

    @Test
    fun `list rejects range more than one year`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/holidays")
                .param("from", "2026-01-01")
                .param("to", "2027-06-01")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `holiday create rejects missing holidayDate`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("name" to "Tanpa Tanggal", "type" to "CUSTOM")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `holiday create rejects blank name`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("holidayDate" to "2026-09-01", "name" to "   ", "type" to "CUSTOM")
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `holiday create rejects invalid type`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/holidays")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("holidayDate" to "2026-09-02", "name" to "Tipe Aneh", "type" to "RANDOM")
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `employee can list holidays without mutation access`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/holidays")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
        )
            .andExpect(status().isOk)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/workday-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
        )
            .andExpect(status().isOk)
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Calendar Test Tenant",
                slug = "cal-${UUID.randomUUID().toString().take(8)}",
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
                email = "cal-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Calendar Test User",
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
