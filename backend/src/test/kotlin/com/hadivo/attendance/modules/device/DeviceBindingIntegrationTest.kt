package com.hadivo.attendance.modules.device

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceAttemptRepository
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttemptReason
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.location.TenantLocation
import com.hadivo.attendance.modules.location.TenantLocationRepository
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceBindingIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var locations: TenantLocationRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var records: AttendanceRecordRepository
    @Autowired private lateinit var attempts: AttendanceAttemptRepository
    @Autowired private lateinit var devices: UserDeviceRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `first clock-in registers trusted device`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)

        clockIn(ctx, "device-a")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.clockInDeviceId").value("device-a"))

        val savedDevices = devices.findActiveTrusted(ctx.tenantId, ctx.userId)
        assertThat(savedDevices).hasSize(1)
        assertThat(savedDevices.first().deviceId).isEqualTo("device-a")
        assertThat(savedDevices.first().trusted).isTrue()
        assertThat(savedDevices.first().active).isTrue()
    }

    @Test
    fun `second clock-out from same device is allowed`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)

        clockIn(ctx, "device-a").andExpect(status().isCreated)
        clockOut(ctx, "device-a")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.clockOutDeviceId").value("device-a"))

        val saved = records.findByTenantIdAndUserIdAndDate(ctx.tenantId, ctx.userId, today())
        assertThat(saved?.clockOutAt).isNotNull()
    }

    @Test
    fun `different device is rejected and logged as mismatch attempt`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)
        clockIn(ctx, "device-a").andExpect(status().isCreated)

        clockOut(ctx, "device-b")
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("DEVICE_MISMATCH"))
            .andExpect(jsonPath("$.error.message").value(DeviceBindingService.DEVICE_MISMATCH_MESSAGE))

        val saved = records.findByTenantIdAndUserIdAndDate(ctx.tenantId, ctx.userId, today())
        assertThat(saved?.clockOutAt).isNull()
        val loggedAttempts = attempts.findAllByTenantIdAndUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            ctx.tenantId,
            ctx.userId,
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(60),
        )
        assertThat(loggedAttempts.first().reason).isEqualTo(AttemptReason.DEVICE_MISMATCH)
        assertThat(loggedAttempts.first().deviceId).isEqualTo("device-b")
    }

    @Test
    fun `blank device id is rejected as invalid device`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)

        clockIn(ctx, "")
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("INVALID_DEVICE"))

        val saved = records.findByTenantIdAndUserIdAndDate(ctx.tenantId, ctx.userId, today())
        assertThat(saved).isNull()
        val loggedAttempts = attempts.findAllByTenantIdAndUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            ctx.tenantId,
            ctx.userId,
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(60),
        )
        assertThat(loggedAttempts.first().reason).isEqualTo(AttemptReason.INVALID_DEVICE)
    }

    @Test
    fun `reset device allows next attendance from new device`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)
        val admin = addUserToTenant(ctx.tenantId, Role.TENANT_ADMIN)
        clockIn(ctx, "device-a").andExpect(status().isCreated)

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/members/${ctx.userId}/devices/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].deviceId").value("device-a"))
            .andExpect(jsonPath("$.data[0].active").value(false))

        clockOut(ctx, "device-b")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.clockOutDeviceId").value("device-b"))

        val activeTrusted = devices.findActiveTrusted(ctx.tenantId, ctx.userId)
        assertThat(activeTrusted).hasSize(1)
        assertThat(activeTrusted.first().deviceId).isEqualTo("device-b")
    }

    @Test
    fun `tenant admin can view member devices`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)
        val admin = addUserToTenant(ctx.tenantId, Role.TENANT_ADMIN)
        clockIn(ctx, "device-a").andExpect(status().isCreated)

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/members/${ctx.userId}/devices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].deviceId").value("device-a"))
            .andExpect(jsonPath("$.data[0].trusted").value(true))
    }

    @Test
    fun `non admin cannot reset device`() {
        val ctx = seedTenantWithUser(role = Role.EMPLOYEE)

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/members/${ctx.userId}/devices/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin from another tenant cannot reset device`() {
        val target = seedTenantWithUser(role = Role.EMPLOYEE)
        val otherTenantAdmin = seedTenantWithUser(role = Role.TENANT_ADMIN)

        mvc.perform(
            post("/api/v1/tenants/${target.tenantId}/members/${target.userId}/devices/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${otherTenantAdmin.token}")
        )
            .andExpect(status().isForbidden)
    }

    private fun clockIn(ctx: TestContext, deviceId: String) =
        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/attendance/clock-in")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType("application/json")
                .content(clockBody(ctx, deviceId))
        )

    private fun clockOut(ctx: TestContext, deviceId: String) =
        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/attendance/clock-out")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType("application/json")
                .content(clockBody(ctx, deviceId))
        )

    private fun clockBody(ctx: TestContext, deviceId: String): String =
        mapper.writeValueAsString(
            mapOf(
                "latitude" to ctx.locationLat,
                "longitude" to ctx.locationLon,
                "deviceId" to deviceId,
                "deviceName" to "Test Phone",
                "platform" to "test",
            )
        )

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Device Test Tenant",
                slug = "device-${UUID.randomUUID().toString().take(8)}",
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
        val location = locations.save(
            TenantLocation(
                tenantId = tenantId,
                name = "HQ",
                latitude = -6.200000,
                longitude = 106.816666,
                radiusMeters = 100,
            )
        )
        val user = seedUser()
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
        return TestContext(
            tenantId = tenantId,
            userId = user.id!!,
            token = jwt.issueAccessToken(user.id!!, user.email).token,
            locationLat = location.latitude,
            locationLon = location.longitude,
        )
    }

    private fun addUserToTenant(tenantId: UUID, role: Role): TestUser {
        val user = seedUser()
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
        return TestUser(
            userId = user.id!!,
            token = jwt.issueAccessToken(user.id!!, user.email).token,
        )
    }

    private fun seedUser(): User =
        users.save(
            User(
                email = "device-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Device Test User",
            )
        )

    private fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Jakarta"))

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val token: String,
        val locationLat: Double,
        val locationLon: Double,
    )

    private data class TestUser(
        val userId: UUID,
        val token: String,
    )
}
