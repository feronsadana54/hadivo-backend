package com.hadivo.attendance.modules.face

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
import org.springframework.beans.factory.annotation.Value
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Base64
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FaceEnrollmentIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var profiles: UserFaceProfileRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository

    @Value("\${hadivo.face.storage-dir}") private lateinit var storageDir: String

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `employee can enroll own face with consent and store image`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val token = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enrollmentStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.consentGiven").value(true))
            .andExpect(jsonPath("$.data.imageStored").value(true))
            .andExpect(jsonPath("$.data.message").exists())

        val profile = profiles.findByTenantIdAndUserId(admin.tenantId, employee.userId)
        assertThat(profile).isNotNull
        assertThat(profile!!.imageReference).isNotBlank()
        assertThat(profile.consentGiven).isTrue
        assertThat(profile.enrolledAt).isNotNull

        val stored = Paths.get(storageDir, profile.imageReference!!)
        assertThat(Files.exists(stored)).isTrue
    }

    @Test
    fun `enroll without consent is rejected`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val token = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to false,
                )))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `enroll rejects non-jpeg-non-png magic bytes`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val token = jwt.issueAccessToken(employee.userId, employee.email).token

        // Random bytes that do not start with JPEG or PNG magic.
        val garbage = Base64.getEncoder().encodeToString(ByteArray(64) { 0x42.toByte() })

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to garbage,
                    "consentGiven" to true,
                )))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `enroll response does not expose imageReference or path`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val token = jwt.issueAccessToken(employee.userId, employee.email).token

        val body = mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn().response.contentAsString

        assertThat(body).doesNotContain("imageReference")
        assertThat(body).doesNotContain("embeddingReference")
        assertThat(body).doesNotContain(storageDir)
        // The sample id should not be echoed back either.
        assertThat(body).doesNotContain(JPEG_SAMPLE_BASE64.take(32))
    }

    @Test
    fun `admin reset clears references and deletes local file`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isOk)

        val before = profiles.findByTenantIdAndUserId(admin.tenantId, employee.userId)!!
        val storedFile: Path = Paths.get(storageDir, before.imageReference!!)
        assertThat(Files.exists(storedFile)).isTrue

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enrollmentStatus").value("RESET"))
            .andExpect(jsonPath("$.data.imageStored").value(false))

        val after = profiles.findByTenantIdAndUserId(admin.tenantId, employee.userId)!!
        assertThat(after.imageReference).isNull()
        assertThat(after.embeddingReference).isNull()
        assertThat(after.resetAt).isNotNull
        assertThat(Files.exists(storedFile)).isFalse
    }

    @Test
    fun `employee cannot reset face profile`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isOk)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `re-enroll after reset creates new stored image reference`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isOk)

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        ).andExpect(status().isOk)

        Thread.sleep(5) // ensure timestamped filename differs
        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to PNG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enrollmentStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.imageStored").value(true))

        val refreshed = profiles.findByTenantIdAndUserId(admin.tenantId, employee.userId)!!
        assertThat(refreshed.imageReference).isNotBlank()
        assertThat(refreshed.resetAt).isNull()
        assertThat(Files.exists(Paths.get(storageDir, refreshed.imageReference!!))).isTrue
    }

    @Test
    fun `re-enroll deletes previous image file`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeToken = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isOk)

        val firstRef = profiles.findByTenantIdAndUserId(admin.tenantId, employee.userId)!!.imageReference!!
        val firstPath = Paths.get(storageDir, firstRef)
        assertThat(Files.exists(firstPath)).isTrue

        Thread.sleep(5)
        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $employeeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to PNG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isOk)

        val secondRef = profiles.findByTenantIdAndUserId(admin.tenantId, employee.userId)!!.imageReference!!
        assertThat(secondRef).isNotEqualTo(firstRef)
        assertThat(Files.exists(firstPath)).isFalse
        assertThat(Files.exists(Paths.get(storageDir, secondRef))).isTrue
    }

    @Test
    fun `audit metadata does not expose base64 or path`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val token = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isOk)

        val faceLogs = auditLogs.findAll()
            .filter { it.tenantId == admin.tenantId && it.action == "FACE_PROFILE_ENROLLED" }
        assertThat(faceLogs).isNotEmpty
        val metadata = faceLogs.first().metadata.orEmpty()
        assertThat(metadata.keys).doesNotContain("imageBase64")
        assertThat(metadata.keys).doesNotContain("imageReference")
        assertThat(metadata.keys).doesNotContain("embeddingReference")
        assertThat(metadata.keys).doesNotContain("path")
        // Ensure no value silently leaks a payload either.
        metadata.values.filterIsInstance<String>().forEach { value ->
            assertThat(value).doesNotContain(JPEG_SAMPLE_BASE64.take(32))
            assertThat(value).doesNotContain(storageDir)
        }
    }

    @Test
    fun `cross user enroll forbidden for employee`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employeeA = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val employeeB = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val tokenA = jwt.issueAccessToken(employeeA.userId, employeeA.email).token

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${employeeB.userId}/face-profile/enroll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "imageBase64" to JPEG_SAMPLE_BASE64,
                    "consentGiven" to true,
                )))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `get face profile returns pending for unenrolled user`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val token = jwt.issueAccessToken(employee.userId, employee.email).token

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/members/${employee.userId}/face-profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enrollmentStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.consentGiven").value(false))
            .andExpect(jsonPath("$.data.imageStored").value(false))
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Face Test Tenant",
                slug = "face-${UUID.randomUUID().toString().take(8)}",
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
                email = "face-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Face Test User",
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

    companion object {
        // Smallest valid JPEG (1x1 white pixel). Encoded once so tests stay self-contained
        // and we never commit a real face photo or biometric sample.
        private val JPEG_SAMPLE_BYTES = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xDB.toByte(), 0x00, 0x43, 0x00,
            0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07,
            0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D, 0x0C, 0x0B, 0x0B,
            0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E,
            0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22,
            0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31,
            0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C,
            0x2E, 0x33, 0x34, 0x32,
            0xFF.toByte(), 0xD9.toByte(),
        )
        val JPEG_SAMPLE_BASE64: String = Base64.getEncoder().encodeToString(JPEG_SAMPLE_BYTES)

        // Minimal PNG magic + chunk so we exercise the PNG branch without committing real images.
        private val PNG_SAMPLE_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00,
            0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        val PNG_SAMPLE_BASE64: String = Base64.getEncoder().encodeToString(PNG_SAMPLE_BYTES)
    }
}
