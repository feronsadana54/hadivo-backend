package com.hadivo.attendance.modules.attendance

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClockInIntegrationTest {

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

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `clock-in inside the radius creates an attendance record`() {
        val ctx = seedTenantWithEmployee()
        val token = jwt.issueAccessToken(ctx.userId, ctx.userEmail).token
        val body = mapper.writeValueAsString(
            mapOf(
                "latitude" to ctx.locationLat,
                "longitude" to ctx.locationLon,
                "deviceId" to "device-test-001",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/attendance/clock-in")
                .header("Authorization", "Bearer $token")
                .contentType("application/json")
                .content(body)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.clockInAt").exists())
            .andExpect(jsonPath("$.data.clockInLocationId").value(ctx.locationId.toString()))

        val saved = records.findByTenantIdAndUserIdAndDate(ctx.tenantId, ctx.userId, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")))
        assertThat(saved).isNotNull
        assertThat(saved!!.clockInDeviceId).isEqualTo("device-test-001")
    }

    @Test
    fun `clock-in outside the radius is rejected and logged as attempt`() {
        val ctx = seedTenantWithEmployee()
        val token = jwt.issueAccessToken(ctx.userId, ctx.userEmail).token
        val body = mapper.writeValueAsString(
            mapOf(
                "latitude" to ctx.locationLat + 1.0,
                "longitude" to ctx.locationLon + 1.0,
                "deviceId" to "device-test-001",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/attendance/clock-in")
                .header("Authorization", "Bearer $token")
                .contentType("application/json")
                .content(body)
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("OUT_OF_RADIUS"))

        val record = records.findByTenantIdAndUserIdAndDate(ctx.tenantId, ctx.userId, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")))
        assertThat(record).isNull()

        val now = Instant.now()
        val loggedAttempts = attempts.findAllByTenantIdAndUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            ctx.tenantId, ctx.userId, now.minusSeconds(60), now.plusSeconds(60),
        )
        assertThat(loggedAttempts).isNotEmpty
        assertThat(loggedAttempts.first().reason).isEqualTo(AttemptReason.OUT_OF_RADIUS)
    }

    private fun seedTenantWithEmployee(): TestContext {
        val user = users.save(
            User(
                email = "employee-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Test Employee",
            )
        )
        val tenant = tenants.save(
            Tenant(
                name = "Test Tenant",
                slug = "test-${UUID.randomUUID().toString().take(8)}",
                mode = TenantMode.COMPANY,
            )
        )
        val tenantId = tenant.id!!
        val userId = user.id!!

        memberships.save(Membership(tenantId = tenantId, userId = userId, role = Role.EMPLOYEE))
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

        return TestContext(
            tenantId = tenantId,
            userId = userId,
            userEmail = user.email,
            locationId = location.id!!,
            locationLat = location.latitude,
            locationLon = location.longitude,
        )
    }

    private data class TestContext(
        val tenantId: UUID,
        val userId: UUID,
        val userEmail: String,
        val locationId: UUID,
        val locationLat: Double,
        val locationLon: Double,
    )
}
