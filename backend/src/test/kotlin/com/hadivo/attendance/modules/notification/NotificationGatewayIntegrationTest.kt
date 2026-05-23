package com.hadivo.attendance.modules.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceAttemptRepository
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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationGatewayIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var memberships: MembershipRepository
    @Autowired private lateinit var subscriptions: SubscriptionRepository
    @Autowired private lateinit var settings: TenantAttendanceSettingsRepository
    @Autowired private lateinit var locations: TenantLocationRepository
    @Autowired private lateinit var attempts: AttendanceAttemptRepository
    @Autowired private lateinit var deliveryLogs: NotificationDeliveryLogRepository
    @Autowired private lateinit var notificationConsumer: NotificationConsumer
    @Autowired private lateinit var notificationService: NotificationService

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `clock-in success creates sent notification delivery logs`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        clockIn(ctx, "device-a").andExpect(status().isCreated)
        consumePublishedNotification()

        val logs = deliveryLogs.findAll().filter { it.tenantId == ctx.tenantId }
        assertThat(logs.map { it.eventType }).contains(NotificationEventType.CLOCK_IN_SUCCESS)
        assertThat(logs.map { it.status }).contains(NotificationDeliveryStatus.SENT)
    }

    @Test
    fun `device mismatch creates notification request and delivery log`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        clockIn(ctx, "device-a").andExpect(status().isCreated)
        clearInvocations(rabbit)

        clockOut(ctx, "device-b").andExpect(status().isUnprocessableEntity)
        consumePublishedNotification()

        val mismatchLogs = deliveryLogs.findAll().filter {
            it.tenantId == ctx.tenantId && it.eventType == NotificationEventType.DEVICE_MISMATCH
        }
        assertThat(mismatchLogs).isNotEmpty
        assertThat(attempts.findAll().filter { it.tenantId == ctx.tenantId }).isNotEmpty
    }

    @Test
    fun `tenant admin can read notification delivery logs`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        val admin = addUserToTenant(ctx.tenantId, Role.TENANT_ADMIN)
        seedDeliveryLog(ctx.tenantId, ctx.userId)

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/notification-deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].eventType").value("CLOCK_IN_SUCCESS"))
            .andExpect(jsonPath("$.data.items[0].channel").value("IN_APP"))
    }

    @Test
    fun `employee and student cannot read notification delivery logs`() {
        val employee = seedTenantWithUser(Role.EMPLOYEE)
        val student = addUserToTenant(employee.tenantId, Role.STUDENT)

        mvc.perform(
            get("/api/v1/tenants/${employee.tenantId}/notification-deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${employee.token}")
        )
            .andExpect(status().isForbidden)

        mvc.perform(
            get("/api/v1/tenants/${employee.tenantId}/notification-deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${student.token}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `super admin tenant member can read notification delivery logs`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        val superAdmin = addUserToTenant(ctx.tenantId, Role.SUPER_ADMIN)
        seedDeliveryLog(ctx.tenantId, ctx.userId)

        mvc.perform(
            get("/api/v1/tenants/${ctx.tenantId}/notification-deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${superAdmin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalItems").value(1))
    }

    @Test
    fun `rabbit publish failure does not fail attendance flow`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        doThrow(IllegalStateException("RabbitMQ unavailable"))
            .`when`(rabbit)
            .convertAndSend(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(NotificationRequest::class.java),
            )

        clockIn(ctx, "device-a").andExpect(status().isCreated)
    }

    @Test
    fun `mock gateway failure is logged as failed delivery`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)

        notificationService.process(
            NotificationRequest(
                eventType = NotificationEventType.CLOCK_IN_SUCCESS,
                tenantId = ctx.tenantId,
                actorUserId = ctx.userId,
                occurredAt = Instant.now(),
                metadata = mapOf("simulateEmailFailure" to true),
            )
        )

        val emailLog = deliveryLogs.findAll().first {
            it.tenantId == ctx.tenantId && it.channel == NotificationChannel.EMAIL
        }
        assertThat(emailLog.status).isEqualTo(NotificationDeliveryStatus.FAILED)
        assertThat(emailLog.errorMessage).contains("Mock email gateway failure")
    }

    private fun consumePublishedNotification() {
        val captor = ArgumentCaptor.forClass(NotificationRequest::class.java)
        verify(rabbit, atLeastOnce()).convertAndSend(
            org.mockito.Mockito.anyString(),
            org.mockito.Mockito.anyString(),
            captor.capture(),
        )
        notificationConsumer.handle(captor.allValues.last())
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

    private fun seedDeliveryLog(tenantId: UUID, recipientUserId: UUID) {
        deliveryLogs.save(
            NotificationDeliveryLog(
                tenantId = tenantId,
                recipientUserId = recipientUserId,
                channel = NotificationChannel.IN_APP,
                eventType = NotificationEventType.CLOCK_IN_SUCCESS,
                destination = recipientUserId.toString(),
                title = "Clock-in berhasil",
                body = "Absensi masuk Anda berhasil tercatat.",
                status = NotificationDeliveryStatus.SENT,
                provider = "in-app",
                sentAt = Instant.now(),
            )
        )
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Notification Test Tenant",
                slug = "notification-${UUID.randomUUID().toString().take(8)}",
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
                email = "notification-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Notification Test User",
            )
        )

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
