package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceRecord
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
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
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceCsvExportIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var records: AttendanceRecordRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `export CSV success returns attachment with attendance rows`() {
        val ctx = seedTenantWithUser(role = Role.TENANT_ADMIN, fullName = "Admin User")
        val employee = seedUserInTenant(ctx.tenantId, fullName = "Doe, \"Jane\"", role = Role.EMPLOYEE)
        val otherTenant = seedTenantWithUser(role = Role.TENANT_ADMIN, fullName = "Other Tenant User")
        val from = LocalDate.of(2026, 1, 5)
        val to = LocalDate.of(2026, 1, 6)

        records.save(
            AttendanceRecord(
                tenantId = ctx.tenantId,
                userId = employee.userId,
                date = from,
                clockInAt = Instant.parse("2026-01-05T01:00:00Z"),
                clockOutAt = Instant.parse("2026-01-05T09:15:00Z"),
                status = AttendanceStatus.COMPLETED,
                workDurationMinutes = 495,
                clockOutOutsideRadius = true,
            )
        )
        records.save(
            AttendanceRecord(
                tenantId = otherTenant.tenantId,
                userId = otherTenant.userId,
                date = from,
                clockInAt = Instant.parse("2026-01-05T02:00:00Z"),
                status = AttendanceStatus.ON_TIME,
            )
        )

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/reports/attendance/export.csv")
                .param("from", from.toString())
                .param("to", to.toString())
                .header("Authorization", "Bearer ${ctx.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv")))
            .andExpect(
                header().string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"hadivo-attendance-report-$from-to-$to.csv\"",
                )
            )
            .andExpect(
                content().string(
                    containsString(
                        "Date,User ID,Full Name,Email,Status,Clock In Time,Clock Out Time,Work Duration Minutes,Clock Out Outside Radius"
                    )
                )
            )
            .andExpect(content().string(containsString("\"Doe, \"\"Jane\"\"\"")))
            .andExpect(content().string(containsString("2026-01-05 01:00:00 UTC")))
            .andExpect(content().string(not(containsString("Other Tenant User"))))
    }

    @Test
    fun `export CSV rejects date range when from is after to`() {
        val ctx = seedTenantWithUser(role = Role.TENANT_ADMIN, fullName = "Admin User")

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/reports/attendance/export.csv")
                .param("from", "2026-01-10")
                .param("to", "2026-01-01")
                .header("Authorization", "Bearer ${ctx.token}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `export CSV rejects range longer than thirty one days`() {
        val ctx = seedTenantWithUser(role = Role.TENANT_ADMIN, fullName = "Admin User")

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/reports/attendance/export.csv")
                .param("from", "2026-01-01")
                .param("to", "2026-02-01")
                .header("Authorization", "Bearer ${ctx.token}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    private fun seedTenantWithUser(role: Role, fullName: String): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Test Tenant",
                slug = "test-${UUID.randomUUID().toString().take(8)}",
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
        val user = seedUserInTenant(tenantId, fullName = fullName, role = role)
        val token = jwt.issueAccessToken(user.userId, user.email).token
        return TestContext(tenantId = tenantId, userId = user.userId, email = user.email, token = token)
    }

    private fun seedUserInTenant(tenantId: UUID, fullName: String, role: Role): TestUser {
        val user = users.save(
            User(
                email = "user-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = fullName,
            )
        )
        val userId = user.id!!
        memberships.save(Membership(tenantId = tenantId, userId = userId, role = role))
        return TestUser(userId = userId, email = user.email)
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
