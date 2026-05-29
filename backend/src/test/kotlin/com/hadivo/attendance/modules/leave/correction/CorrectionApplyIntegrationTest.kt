package com.hadivo.attendance.modules.leave.correction

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.attendance.AttendanceAttempt
import com.hadivo.attendance.modules.attendance.AttendanceAttemptRepository
import com.hadivo.attendance.modules.attendance.AttendanceRecord
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.attendance.AttendanceType
import com.hadivo.attendance.modules.attendance.AttemptReason
import com.hadivo.attendance.modules.audit.AuditLogRepository
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.leave.LeaveRequest
import com.hadivo.attendance.modules.leave.LeaveRequestRepository
import com.hadivo.attendance.modules.leave.LeaveRequestStatus
import com.hadivo.attendance.modules.leave.LeaveRequestType
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
import org.hamcrest.Matchers.containsString
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrectionApplyIntegrationTest {

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
    @Autowired private lateinit var attempts: AttendanceAttemptRepository
    @Autowired private lateinit var leaves: LeaveRequestRepository
    @Autowired private lateinit var applies: AttendanceCorrectionApplyRepository
    @Autowired private lateinit var auditLogs: AuditLogRepository

    @MockBean private lateinit var rabbit: RabbitTemplate

    @Test
    fun `approving correction with existing record updates clock-in and clock-out`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 1)
        val originalIn = Instant.parse("2026-06-01T02:30:00Z")
        val record = records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = originalIn,
                clockInLatitude = -6.2,
                clockInLongitude = 106.8,
                clockInDeviceId = "device-original",
                status = AttendanceStatus.LATE,
                clockOutOutsideRadius = false,
            )
        )
        val requestedIn = Instant.parse("2026-06-01T01:00:00Z")
        val requestedOut = Instant.parse("2026-06-01T09:30:00Z")
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = requestedIn,
                requestedClockOutAt = requestedOut,
                correctionNote = "Lupa clock-in dan clock-out",
            )
        )

        mvc.perform(approveBy(admin, leave.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))

        val updated = records.findById(record.id!!).get()
        assertThat(updated.clockInAt).isEqualTo(requestedIn)
        assertThat(updated.clockOutAt).isEqualTo(requestedOut)
        assertThat(updated.correctionApplied).isTrue()
        assertThat(updated.correctionRequestId).isEqualTo(leave.id)
        assertThat(updated.correctedBy).isEqualTo(admin.userId)
        assertThat(updated.correctedAt).isNotNull()
        assertThat(updated.correctionNote).isEqualTo("Lupa clock-in dan clock-out")
        assertThat(updated.workDurationMinutes).isEqualTo(510)
    }

    @Test
    fun `apply audit row stores original clock-in clock-out and status`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 2)
        val originalIn = Instant.parse("2026-06-02T02:30:00Z")
        records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = originalIn,
                status = AttendanceStatus.LATE,
                clockOutOutsideRadius = false,
            )
        )
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-02T01:00:00Z"),
            )
        )

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        val applyRow = applies.findByLeaveRequestId(leave.id!!)
        assertThat(applyRow).isNotNull
        assertThat(applyRow!!.originalClockInAt).isEqualTo(originalIn)
        assertThat(applyRow.originalClockOutAt).isNull()
        assertThat(applyRow.originalStatus).isEqualTo(AttendanceStatus.LATE)
        assertThat(applyRow.appliedClockInAt).isEqualTo(Instant.parse("2026-06-02T01:00:00Z"))
        assertThat(applyRow.appliedStatus).isIn(AttendanceStatus.ON_TIME, AttendanceStatus.LATE)
        assertThat(applyRow.appliedBy).isEqualTo(admin.userId)
        assertThat(applyRow.reviewerUserId).isEqualTo(admin.userId)
        assertThat(applyRow.requesterUserId).isEqualTo(employee.userId)
        assertThat(applyRow.recordCreatedByCorrection).isFalse()
    }

    @Test
    fun `approving same correction twice is idempotent`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 3)
        records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = Instant.parse("2026-06-03T02:00:00Z"),
                status = AttendanceStatus.ON_TIME,
                clockOutOutsideRadius = false,
            )
        )
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-03T01:00:00Z"),
            )
        )

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        // Force back to PENDING so we can hit /approve again without status conflict
        val current = leaves.findById(leave.id!!).get()
        current.status = LeaveRequestStatus.PENDING
        leaves.save(current)

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        val allApplies = applies.findAll().filter { it.leaveRequestId == leave.id }
        assertThat(allApplies).hasSize(1)
        assertThat(auditLogs.findAll().filter { it.action == "ATTENDANCE_CORRECTION_ALREADY_APPLIED" })
            .isNotEmpty()
    }

    @Test
    fun `approving correction with no existing record creates correction-generated record`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 4)
        val requestedIn = Instant.parse("2026-06-04T01:00:00Z")
        val requestedOut = Instant.parse("2026-06-04T09:30:00Z")
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = requestedIn,
                requestedClockOutAt = requestedOut,
                correctionNote = "Tidak sempat clock-in dari mobile",
            )
        )

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        val created = records.findByTenantIdAndUserIdAndDate(admin.tenantId, employee.userId, date)
        assertThat(created).isNotNull
        assertThat(created!!.correctionApplied).isTrue()
        assertThat(created.correctionRequestId).isEqualTo(leave.id)
        assertThat(created.clockInAt).isEqualTo(requestedIn)
        assertThat(created.clockOutAt).isEqualTo(requestedOut)
        // No fake device / geofence / face data on correction-generated records
        assertThat(created.clockInLatitude).isNull()
        assertThat(created.clockInLongitude).isNull()
        assertThat(created.clockOutLatitude).isNull()
        assertThat(created.clockOutLongitude).isNull()
        assertThat(created.clockInDeviceId).isNull()
        assertThat(created.clockOutDeviceId).isNull()
        assertThat(created.clockInLocationId).isNull()
        assertThat(created.clockOutLocationId).isNull()

        val applyRow = applies.findByLeaveRequestId(leave.id!!)
        assertThat(applyRow).isNotNull
        assertThat(applyRow!!.recordCreatedByCorrection).isTrue()
        assertThat(applyRow.originalClockInAt).isNull()
        assertThat(applyRow.originalStatus).isNull()
    }

    @Test
    fun `non-correction leave approval does not mutate attendance records`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 5)
        val originalIn = Instant.parse("2026-06-05T01:00:00Z")
        val record = records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = originalIn,
                clockInLatitude = -6.2,
                clockInLongitude = 106.8,
                clockInDeviceId = "device-A",
                status = AttendanceStatus.ON_TIME,
                clockOutOutsideRadius = false,
            )
        )
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.SICK,
                startDate = date,
                endDate = date,
                reason = "Demam",
            )
        )

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        val untouched = records.findById(record.id!!).get()
        assertThat(untouched.clockInAt).isEqualTo(originalIn)
        assertThat(untouched.correctionApplied).isFalse()
        assertThat(untouched.correctionRequestId).isNull()
        assertThat(untouched.correctedBy).isNull()
        assertThat(untouched.clockInLatitude).isEqualTo(-6.2)
        assertThat(untouched.clockInDeviceId).isEqualTo("device-A")
        assertThat(applies.findByLeaveRequestId(leave.id!!)).isNull()
    }

    @Test
    fun `attendance attempts and geofence data are not touched after correction apply`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 6)
        val attemptCountBefore = attempts.count()
        attempts.save(
            AttendanceAttempt(
                tenantId = admin.tenantId,
                userId = employee.userId,
                type = AttendanceType.CLOCK_IN,
                reason = AttemptReason.OUT_OF_RADIUS,
            )
        )
        records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = Instant.parse("2026-06-06T02:30:00Z"),
                clockInLatitude = -6.2,
                clockInLongitude = 106.8,
                clockInDeviceId = "device-real",
                clockInLocationId = null,
                status = AttendanceStatus.LATE,
                clockOutOutsideRadius = false,
            )
        )
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-06T01:00:00Z"),
            )
        )

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        // Attempts table count after = before + 1 (the seed) only
        assertThat(attempts.count()).isEqualTo(attemptCountBefore + 1)

        val record = records.findByTenantIdAndUserIdAndDate(admin.tenantId, employee.userId, date)!!
        // Geofence/device data preserved
        assertThat(record.clockInLatitude).isEqualTo(-6.2)
        assertThat(record.clockInLongitude).isEqualTo(106.8)
        assertThat(record.clockInDeviceId).isEqualTo("device-real")
    }

    @Test
    fun `daily report exposes correctionApplied and correctionRequestId`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 7)
        records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = Instant.parse("2026-06-07T02:30:00Z"),
                status = AttendanceStatus.LATE,
                clockOutOutsideRadius = false,
            )
        )
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-07T01:00:00Z"),
            )
        )
        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/reports/attendance/daily")
                .param("date", date.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.rows[0].correctionApplied").value(true))
            .andExpect(jsonPath("$.data.rows[0].correctionRequestId").value(leave.id.toString()))
    }

    @Test
    fun `csv export includes Correction Applied and Correction Request ID columns`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 8)
        records.save(
            AttendanceRecord(
                tenantId = admin.tenantId,
                userId = employee.userId,
                date = date,
                clockInAt = Instant.parse("2026-06-08T02:00:00Z"),
                status = AttendanceStatus.ON_TIME,
                clockOutOutsideRadius = false,
            )
        )
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-08T01:00:00Z"),
            )
        )
        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        mvc.perform(
            get("/api/v1/tenants/${admin.tenantId}/reports/attendance/export.csv")
                .param("from", date.toString())
                .param("to", date.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.token}")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Correction Applied,Correction Request ID")))
            .andExpect(content().string(containsString("true,${leave.id}")))
    }

    @Test
    fun `audit log records both APPROVED and APPLIED events for correction`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 9)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-09T01:00:00Z"),
            )
        )

        mvc.perform(approveBy(admin, leave.id)).andExpect(status().isOk)

        val actions = auditLogs.findAll().filter { it.tenantId == admin.tenantId }.map { it.action }
        assertThat(actions).contains(
            "LEAVE_REQUEST_APPROVED",
            "ATTENDANCE_CORRECTION_APPROVED",
            "ATTENDANCE_CORRECTION_APPLIED",
        )
    }

    @Test
    fun `notification publish failure does not break correction apply`() {
        val admin = seedTenantWithUser(Role.TENANT_ADMIN)
        val employee = seedUserInTenant(admin.tenantId, Role.EMPLOYEE)
        val date = LocalDate.of(2026, 6, 10)
        val leave = leaves.save(
            LeaveRequest(
                tenantId = admin.tenantId,
                requesterUserId = employee.userId,
                requestType = LeaveRequestType.ATTENDANCE_CORRECTION,
                startDate = date,
                endDate = date,
                requestedClockInAt = Instant.parse("2026-06-10T01:00:00Z"),
            )
        )

        doThrow(IllegalStateException("RabbitMQ unavailable"))
            .`when`(rabbit)
            .convertAndSend(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(NotificationRequest::class.java),
            )

        mvc.perform(approveBy(admin, leave.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))

        assertThat(applies.findByLeaveRequestId(leave.id!!)).isNotNull
    }

    private fun approveBy(ctx: TestContext, leaveId: UUID?) =
        post("/api/v1/tenants/${ctx.tenantId}/leave-requests/$leaveId/approve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${ctx.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("reviewNote" to "OK")))

    private fun seedTenantWithUser(role: Role): TestContext {
        val tenant = tenants.save(
            Tenant(
                name = "Correction Test Tenant",
                slug = "correction-${UUID.randomUUID().toString().take(8)}",
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
                email = "correction-${UUID.randomUUID()}@test.local",
                passwordHash = passwordEncoder.encode("Test12345!"),
                fullName = "Correction Test User",
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
