package com.hadivo.attendance.modules.shift

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.audit.AuditLogRepository
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftIntegrationTest {

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
    @Autowired private lateinit var records: AttendanceRecordRepository
    @Autowired private lateinit var shifts: ShiftTemplateRepository
    @Autowired private lateinit var assignments: MemberShiftAssignmentRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository
    @Autowired private lateinit var resolver: ShiftScheduleResolver

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `tenant admin can create and update shift`() {
        val ctx = seedTenantWithUser(Role.TENANT_ADMIN)

        val createResponse = mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/shifts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "name" to "Shift Pagi",
                            "startTime" to "07:00",
                            "endTime" to "15:00",
                            "lateThresholdMinutes" to 10,
                            "allowsOvertime" to true,
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.name").value("Shift Pagi"))
            .andExpect(jsonPath("$.data.overnight").value(false))
            .andReturn()
            .response

        val shiftId = mapper.readTree(createResponse.contentAsString).get("data").get("id").asText()

        mvc.perform(
            patch("/api/v1/tenants/${ctx.tenantId}/shifts/$shiftId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("name" to "Shift Pagi Utama", "active" to true)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("Shift Pagi Utama"))

        assertThat(auditLogs.findAll().filter { it.tenantId == ctx.tenantId }.map { it.action })
            .contains("SHIFT_CREATED", "SHIFT_UPDATED")
    }

    @ParameterizedTest
    @EnumSource(value = Role::class, names = ["EMPLOYEE", "STUDENT"])
    fun `employee and student cannot create shift`(role: Role) {
        val ctx = seedTenantWithUser(role)

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/shifts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("name" to "Shift Pagi", "startTime" to "07:00", "endTime" to "15:00")
                    )
                )
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `cross tenant shift access is rejected`() {
        val tenantA = seedTenantWithUser(Role.TENANT_ADMIN)
        val tenantB = seedTenantWithUser(Role.TENANT_ADMIN)
        val shift = shifts.save(
            ShiftTemplate(
                tenantId = tenantB.tenantId,
                name = "Tenant B Shift",
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(16, 0),
            )
        )

        mvc.perform(
            patch("/api/v1/tenants/${tenantB.tenantId}/shifts/${shift.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantA.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("name" to "Illegal Update")))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `tenant admin can assign shift and overlapping active assignment is rejected`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val member = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val shift = shifts.save(
            ShiftTemplate(
                tenantId = admin.tenantId,
                name = "Shift Siang",
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(21, 0),
            )
        )
        val body = mapper.writeValueAsString(
            mapOf(
                "shiftTemplateId" to shift.id.toString(),
                "effectiveFrom" to "2026-01-01",
                "effectiveTo" to "2026-01-31",
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${member.userId}/shift-assignments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.shiftName").value("Shift Siang"))

        mvc.perform(
            post("/api/v1/tenants/${admin.tenantId}/members/${member.userId}/shift-assignments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))

        assertThat(auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action })
            .contains("SHIFT_ASSIGNMENT_CREATED")
    }

    @Test
    fun `clock-in uses assigned shift late threshold`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        seedLocation(ctx.tenantId)
        val now = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
        val assignedStart = if (now.toLocalTime().isBefore(LocalTime.of(0, 30))) {
            LocalTime.MIDNIGHT
        } else {
            now.minusMinutes(10).toLocalTime().withSecond(0).withNano(0)
        }
        settings.findById(ctx.tenantId).get().apply {
            workStartTime = now.minusHours(1).toLocalTime().withSecond(0).withNano(0)
            lateThresholdMinutes = 0
        }.also(settings::save)
        val shift = shifts.save(
            ShiftTemplate(
                tenantId = ctx.tenantId,
                name = "Flexible Shift",
                startTime = assignedStart,
                endTime = assignedStart.plusHours(8),
                lateThresholdMinutes = 30,
            )
        )
        assignments.save(
            MemberShiftAssignment(
                tenantId = ctx.tenantId,
                userId = ctx.userId,
                shiftTemplateId = shift.id!!,
                effectiveFrom = now.toLocalDate(),
            )
        )

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/attendance/clock-in")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(clockBody("assigned-shift-device"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("ON_TIME"))
            .andExpect(jsonPath("$.data.shiftName").value("Flexible Shift"))

        val saved = records.findByTenantIdAndUserIdAndDate(ctx.tenantId, ctx.userId, now.toLocalDate())
        assertThat(saved?.status).isEqualTo(AttendanceStatus.ON_TIME)
        assertThat(saved?.shiftName).isEqualTo("Flexible Shift")
    }

    @Test
    fun `member without shift falls back to tenant attendance settings`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        seedLocation(ctx.tenantId)
        val now = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
        settings.findById(ctx.tenantId).get().apply {
            workStartTime = if (now.toLocalTime().isBefore(LocalTime.of(0, 30))) {
                LocalTime.MIDNIGHT
            } else {
                now.minusMinutes(10).toLocalTime().withSecond(0).withNano(0)
            }
            lateThresholdMinutes = 0
            allowLateClockIn = true
        }.also(settings::save)

        mvc.perform(
            post("/api/v1/tenants/${ctx.tenantId}/attendance/clock-in")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(clockBody("fallback-shift-device"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("LATE"))
            .andExpect(jsonPath("$.data.shiftId").doesNotExist())
            .andExpect(jsonPath("$.data.scheduledStartTime").exists())
    }

    @Test
    fun `overnight shift resolves early morning to previous shift date`() {
        val ctx = seedTenantWithUser(Role.EMPLOYEE)
        val shiftDate = LocalDate.of(2026, 1, 5)
        val shift = shifts.save(
            ShiftTemplate(
                tenantId = ctx.tenantId,
                name = "Shift Malam",
                startTime = LocalTime.of(22, 0),
                endTime = LocalTime.of(6, 0),
                lateThresholdMinutes = 15,
            )
        )
        assignments.save(
            MemberShiftAssignment(
                tenantId = ctx.tenantId,
                userId = ctx.userId,
                shiftTemplateId = shift.id!!,
                effectiveFrom = shiftDate,
                effectiveTo = shiftDate,
            )
        )
        val resolved = resolver.resolve(
            tenantId = ctx.tenantId,
            userId = ctx.userId,
            settings = settings.findById(ctx.tenantId).get(),
            now = ZonedDateTime.of(2026, 1, 6, 5, 30, 0, 0, ZoneId.of("Asia/Jakarta")),
        )

        assertThat(resolved.attendanceDate).isEqualTo(shiftDate)
        assertThat(resolved.shiftName).isEqualTo("Shift Malam")
        assertThat(resolved.overnight).isTrue()
    }

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Shift Test Tenant",
                slug = "shift-${UUID.randomUUID().toString().take(8)}",
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
        return TestContext(
            tenantId = tenantId,
            userId = user.userId,
            email = user.email,
            token = jwt.issueAccessToken(user.userId, user.email).token,
        )
    }

    private fun seedUserInTenant(tenantId: UUID, role: Role): TestUser {
        val user = users.save(
            User(
                email = "shift-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Shift Test User",
            )
        )
        memberships.save(Membership(tenantId = tenantId, userId = user.id!!, role = role))
        return TestUser(userId = user.id!!, email = user.email)
    }

    private fun seedLocation(tenantId: UUID) {
        locations.save(
            TenantLocation(
                tenantId = tenantId,
                name = "HQ",
                latitude = -6.200000,
                longitude = 106.816666,
                radiusMeters = 100,
            )
        )
    }

    private fun clockBody(deviceId: String): String =
        mapper.writeValueAsString(
            mapOf(
                "latitude" to -6.200000,
                "longitude" to 106.816666,
                "deviceId" to deviceId,
            )
        )

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
