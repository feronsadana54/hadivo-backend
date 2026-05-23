package com.hadivo.attendance.modules.superadmin

import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceAttempt
import com.hadivo.attendance.modules.attendance.AttendanceAttemptRepository
import com.hadivo.attendance.modules.attendance.AttendanceRecord
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.attendance.AttendanceType
import com.hadivo.attendance.modules.attendance.AttemptReason
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuperAdminIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var records: AttendanceRecordRepository
    @Autowired private lateinit var attempts: AttendanceAttemptRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `super admin can access overview`() {
        val superAdmin = seedSuperAdmin()

        mvc.perform(
            get("/api/v1/super-admin/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${superAdmin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalTenants").exists())
            .andExpect(jsonPath("$.data.activeTenants").exists())
            .andExpect(jsonPath("$.data.generatedAt").exists())
    }

    @ParameterizedTest
    @EnumSource(value = Role::class, names = ["SUPER_ADMIN"], mode = EnumSource.Mode.EXCLUDE)
    fun `non super admin roles are rejected`(role: Role) {
        val nonSuperAdmin = seedTenantWithUser(role = role)

        mvc.perform(
            get("/api/v1/super-admin/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${nonSuperAdmin.token}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
    }

    @Test
    fun `tenant list returns demo tenant data`() {
        val superAdmin = seedSuperAdmin()

        mvc.perform(
            get("/api/v1/super-admin/tenants")
                .param("search", "Hadivo Demo")
                .param("page", "0")
                .param("size", "10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${superAdmin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].tenantId").value(DEMO_TENANT_ID.toString()))
            .andExpect(jsonPath("$.data.items[0].tenantName").value("Hadivo Demo School"))
            .andExpect(jsonPath("$.data.items[0].tenantType").value("SCHOOL"))
            .andExpect(jsonPath("$.data.items[0].subscriptionStatus").value("ACTIVE"))
    }

    @Test
    fun `tenant detail returns member and attendance summary without tenant membership dependency`() {
        val superAdmin = seedSuperAdmin()
        val tenant = seedTenantWithUser(role = Role.EMPLOYEE)

        records.save(
            AttendanceRecord(
                tenantId = tenant.tenantId,
                userId = tenant.userId,
                date = LocalDate.now(ZoneId.of("Asia/Jakarta")),
                clockInAt = Instant.now(),
                status = AttendanceStatus.ON_TIME,
            )
        )
        attempts.save(
            AttendanceAttempt(
                tenantId = tenant.tenantId,
                userId = tenant.userId,
                type = AttendanceType.CLOCK_IN,
                reason = AttemptReason.OUT_OF_RADIUS,
                latitude = -6.2,
                longitude = 106.8,
                deviceId = "test-device",
            )
        )

        mvc.perform(
            get("/api/v1/super-admin/tenants/${tenant.tenantId}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${superAdmin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tenantId").value(tenant.tenantId.toString()))
            .andExpect(jsonPath("$.data.memberCount").value(1))
            .andExpect(jsonPath("$.data.activeMemberCount").value(1))
            .andExpect(jsonPath("$.data.attendanceToday").value(1))
            .andExpect(jsonPath("$.data.failedAttemptsToday").value(1))
            .andExpect(jsonPath("$.data.recentFailedAttempts[0].reason").value("OUT_OF_RADIUS"))
    }

    @Test
    fun `super admin response does not expose password or token fields`() {
        val superAdmin = seedSuperAdmin()

        mvc.perform(
            get("/api/v1/super-admin/tenants")
                .param("search", "Hadivo Demo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${superAdmin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("passwordHash"))))
            .andExpect(content().string(not(containsString("refreshToken"))))
            .andExpect(content().string(not(containsString("accessToken"))))
            .andExpect(content().string(not(containsString("secret"))))
    }

    private fun seedSuperAdmin(): TestUser {
        val user = seedUser(fullName = "Super Admin")
        memberships.save(Membership(tenantId = DEMO_TENANT_ID, userId = user.id!!, role = Role.SUPER_ADMIN))
        return TestUser(userId = user.id!!, email = user.email, token = jwt.issueAccessToken(user.id!!, user.email).token)
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Super Admin Test Tenant",
                slug = "super-admin-${UUID.randomUUID().toString().take(8)}",
                mode = TenantMode.COMPANY,
                timezone = "Asia/Jakarta",
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

        val user = seedUser(fullName = "Super Admin Test User")
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))

        return TestContext(
            tenantId = tenantId,
            userId = user.id!!,
            token = jwt.issueAccessToken(user.id!!, user.email).token,
        )
    }

    private fun seedUser(fullName: String): User =
        users.save(
            User(
                email = "super-admin-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = fullName,
            )
        )

    private data class TestUser(
        val userId: UUID,
        val email: String,
        val token: String,
    )

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val token: String,
    )

    private companion object {
        val DEMO_TENANT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
