package com.hadivo.attendance.modules.security

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
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `cross tenant membership access is rejected`() {
        val tenantA = seedTenantWithUser(role = Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(role = Role.TENANT_ADMIN)

        mvc.perform(
            get("/api/v1/tenants/${tenantB.tenantId}/memberships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.accessToken}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
    }

    @Test
    fun `login failures lock account temporarily without revealing account existence`() {
        val email = "lockout-${UUID.randomUUID()}@test.local"
        seedUser(email = email, password = VALID_PASSWORD)

        repeat(5) {
            mvc.perform(loginRequest(email, "WrongPassword1"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.message").value("Email atau password tidak sesuai."))
        }

        mvc.perform(loginRequest(email, "WrongPassword1"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.message").value("Terlalu banyak percobaan login gagal. Coba lagi beberapa menit lagi."))
    }

    @Test
    fun `successful login resets failed login counter`() {
        val email = "reset-${UUID.randomUUID()}@test.local"
        seedUser(email = email, password = VALID_PASSWORD)

        mvc.perform(loginRequest(email, "WrongPassword1"))
            .andExpect(status().isUnauthorized)

        mvc.perform(loginRequest(email, VALID_PASSWORD))
            .andExpect(status().isOk)

        repeat(4) {
            mvc.perform(loginRequest(email, "WrongPassword1"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.message").value("Email atau password tidak sesuai."))
        }

        mvc.perform(loginRequest(email, VALID_PASSWORD))
            .andExpect(status().isOk)
    }

    @Test
    fun `register enforces password policy`() {
        val body = mapper.writeValueAsString(
            mapOf(
                "email" to "weak-${UUID.randomUUID()}@test.local",
                "password" to "abcdefgh",
                "fullName" to "Weak Password User",
            )
        )

        mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(body)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.message").value("Password minimal 8 karakter dan harus memiliki huruf serta angka."))
    }

    @Test
    fun `refresh token rotation rejects old token and logout revokes current token`() {
        val email = "refresh-${UUID.randomUUID()}@test.local"
        seedUser(email = email, password = VALID_PASSWORD)
        val firstLogin = login(email, VALID_PASSWORD)
        val firstRefresh = firstLogin["refreshToken"] as String

        val refreshed = refresh(firstRefresh)
        val rotatedRefresh = refreshed["refreshToken"] as String

        mvc.perform(refreshRequest(firstRefresh))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))

        mvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${refreshed["accessToken"]}")
                .contentType("application/json")
                .content(mapper.writeValueAsString(mapOf("refreshToken" to rotatedRefresh)))
        )
            .andExpect(status().isNoContent)

        mvc.perform(refreshRequest(rotatedRefresh))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `security headers are present on backend responses`() {
        mvc.perform(get("/actuator/health"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
    }

    @Test
    fun `settings update and CSV export are audited`() {
        val ctx = seedTenantWithUser(role = Role.TENANT_ADMIN)

        mvc.perform(
            patch("/api/v1/tenants/${ctx.tenantId}/attendance-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.accessToken}")
                .contentType("application/json")
                .content(mapper.writeValueAsString(mapOf("allowLateClockIn" to false)))
        )
            .andExpect(status().isOk)

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/reports/attendance/export.csv")
                .param("from", LocalDate.of(2026, 1, 1).toString())
                .param("to", LocalDate.of(2026, 1, 2).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.accessToken}")
        )
            .andExpect(status().isOk)

        val tenantAuditLogs = auditLogs.findAll().filter { it.tenantId == ctx.tenantId }
        assertThat(tenantAuditLogs.map { it.action }).contains("ATTENDANCE_SETTINGS_UPDATED", "REPORT_CSV_EXPORTED")
    }

    private fun loginRequest(email: String, password: String) =
        post("/api/v1/auth/login")
            .contentType("application/json")
            .content(mapper.writeValueAsString(mapOf("email" to email, "password" to password)))

    private fun refreshRequest(refreshToken: String) =
        post("/api/v1/auth/refresh")
            .contentType("application/json")
            .content(mapper.writeValueAsString(mapOf("refreshToken" to refreshToken)))

    private fun login(email: String, password: String): Map<*, *> {
        val response = mvc.perform(loginRequest(email, password))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return mapper.readValue(response, Map::class.java)["data"] as Map<*, *>
    }

    private fun refresh(refreshToken: String): Map<*, *> {
        val response = mvc.perform(refreshRequest(refreshToken))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return mapper.readValue(response, Map::class.java)["data"] as Map<*, *>
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val user = seedUser(email = "user-${UUID.randomUUID()}@test.local", password = VALID_PASSWORD)
        val tenant = tenants.save(
            Tenant(
                name = "Security Test Tenant",
                slug = "security-${UUID.randomUUID().toString().take(8)}",
                mode = TenantMode.COMPANY,
            )
        )
        val tenantId = tenant.id!!
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
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

        return TestContext(
            tenantId = tenantId,
            userId = user.id!!,
            accessToken = jwt.issueAccessToken(user.id!!, user.email).token,
        )
    }

    private fun seedUser(email: String, password: String): User =
        users.save(
            User(
                email = email.lowercase(),
                passwordHash = passwordEncoder.encode(password),
                fullName = "Security Test User",
            )
        )

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val accessToken: String,
    )

    private companion object {
        const val VALID_PASSWORD = "Test12345!"
    }
}
