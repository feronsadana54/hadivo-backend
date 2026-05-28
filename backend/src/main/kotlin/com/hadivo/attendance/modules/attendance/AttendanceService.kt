package com.hadivo.attendance.modules.attendance

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.util.TimeUtils
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.device.DeviceBindingCommand
import com.hadivo.attendance.modules.device.DeviceBindingService
import com.hadivo.attendance.modules.face.FaceVerifier
import com.hadivo.attendance.modules.geofence.GeofenceValidator
import com.hadivo.attendance.modules.location.LocationService
import com.hadivo.attendance.modules.settings.SettingsService
import com.hadivo.attendance.modules.shift.ResolvedShiftSchedule
import com.hadivo.attendance.modules.shift.ShiftScheduleResolver
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class AttendanceService(
    private val records: AttendanceRecordRepository,
    private val attempts: AttendanceAttemptRepository,
    private val attemptLogger: AttemptLogger,
    private val deviceBinding: DeviceBindingService,
    private val settingsService: SettingsService,
    private val shiftScheduleResolver: ShiftScheduleResolver,
    private val locationService: LocationService,
    private val geofence: GeofenceValidator,
    private val faceVerifier: FaceVerifier,
    private val audit: AuditLogger,
    private val publisher: ApplicationEventPublisher,
    private val users: UserRepository,
) {

    @Transactional
    fun clockIn(tenantId: UUID, userId: UUID, request: ClockInRequest): AttendanceRecord {
        val settings = settingsService.get(tenantId)
        val now = TimeUtils.nowAt(settings.timezone)
        val schedule = shiftScheduleResolver.resolve(tenantId, userId, settings, now)
        val attendanceDate = schedule.attendanceDate
        val deviceResult = deviceBinding.ensureAllowedForAttendance(
            tenantId = tenantId,
            userId = userId,
            type = AttendanceType.CLOCK_IN,
            command = DeviceBindingCommand(
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                platform = request.platform,
            ),
            latitude = request.latitude,
            longitude = request.longitude,
        )

        records.findByTenantIdAndUserIdAndDate(tenantId, userId, attendanceDate)?.let { existing ->
            if (existing.clockInAt != null) {
                logAttempt(tenantId, userId, AttendanceType.CLOCK_IN, AttemptReason.DUPLICATE_CLOCK_IN, request.latitude, request.longitude, request.deviceId)
                throw DomainException(ErrorCode.DUPLICATE_CLOCK_IN)
            }
        }

        val location = geofence.findMatching(request.latitude, request.longitude, locationService.activeFor(tenantId))
            ?: run {
                logAttempt(tenantId, userId, AttendanceType.CLOCK_IN, AttemptReason.OUT_OF_RADIUS, request.latitude, request.longitude, request.deviceId)
                throw DomainException(ErrorCode.OUT_OF_RADIUS)
            }

        if (settings.requireFaceClockIn && !faceVerifier.verify(userId, request.faceImageBase64)) {
            logAttempt(tenantId, userId, AttendanceType.CLOCK_IN, AttemptReason.FACE_MISMATCH, request.latitude, request.longitude, request.deviceId)
            throw DomainException(ErrorCode.FACE_MISMATCH)
        }

        val isLate = schedule.isLate(now)
        if (isLate && !settings.allowLateClockIn) {
            logAttempt(tenantId, userId, AttendanceType.CLOCK_IN, AttemptReason.LATE_NOT_ALLOWED, request.latitude, request.longitude, request.deviceId)
            throw DomainException(ErrorCode.LATE_NOT_ALLOWED)
        }

        val record = records.save(
            AttendanceRecord(
                tenantId = tenantId,
                userId = userId,
                date = attendanceDate,
                clockInAt = now.toInstant(),
                clockInLocationId = location.id,
                clockInLatitude = request.latitude,
                clockInLongitude = request.longitude,
                clockInDeviceId = deviceResult.device.deviceId,
                status = if (isLate) AttendanceStatus.LATE else AttendanceStatus.ON_TIME,
                shiftTemplateId = schedule.shiftId,
                shiftName = schedule.shiftName,
                scheduledStartTime = schedule.startTime,
                scheduledEndTime = schedule.endTime,
                lateThresholdMinutes = schedule.lateThresholdMinutes,
            )
        )

        if (deviceResult.registered) {
            deviceBinding.auditRegistered(tenantId, userId, deviceResult.device)
        }

        audit.log(
            tenantId = tenantId,
            actorUserId = userId,
            action = "ATTENDANCE_CLOCK_IN",
            resourceType = "AttendanceRecord",
            resourceId = record.id.toString(),
            metadata = mapOf(
                "status" to record.status.name,
                "locationId" to location.id?.toString(),
                "shiftId" to schedule.shiftId?.toString(),
                "shiftName" to schedule.shiftName,
            ),
        )

        publisher.publishEvent(
            ClockInOccurred(
                tenantId = tenantId,
                userId = userId,
                recordId = record.id ?: error("record id null"),
                status = record.status,
                occurredAt = record.clockInAt!!,
            )
        )
        return record
    }

    @Transactional
    fun clockOut(tenantId: UUID, userId: UUID, request: ClockOutRequest): AttendanceRecord {
        val settings = settingsService.get(tenantId)
        val now = TimeUtils.nowAt(settings.timezone)
        val schedule = shiftScheduleResolver.resolve(tenantId, userId, settings, now)
        val deviceResult = deviceBinding.ensureAllowedForAttendance(
            tenantId = tenantId,
            userId = userId,
            type = AttendanceType.CLOCK_OUT,
            command = DeviceBindingCommand(
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                platform = request.platform,
            ),
            latitude = request.latitude,
            longitude = request.longitude,
        )

        val record = records.findByTenantIdAndUserIdAndDate(tenantId, userId, schedule.attendanceDate)
            ?: run {
                logAttempt(tenantId, userId, AttendanceType.CLOCK_OUT, AttemptReason.NO_CLOCK_IN, request.latitude, request.longitude, request.deviceId)
                throw DomainException(ErrorCode.NO_CLOCK_IN)
            }
        if (record.clockOutAt != null) {
            logAttempt(tenantId, userId, AttendanceType.CLOCK_OUT, AttemptReason.ALREADY_CLOCKED_OUT, request.latitude, request.longitude, request.deviceId)
            throw DomainException(ErrorCode.ALREADY_CLOCKED_OUT)
        }

        val activeLocations = locationService.activeFor(tenantId)
        val matchedLocation = geofence.findMatching(request.latitude, request.longitude, activeLocations)
        if (matchedLocation == null && !settings.allowClockOutOutsideRadius) {
            logAttempt(tenantId, userId, AttendanceType.CLOCK_OUT, AttemptReason.OUT_OF_RADIUS, request.latitude, request.longitude, request.deviceId)
            throw DomainException(ErrorCode.OUT_OF_RADIUS)
        }

        if (settings.requireFaceClockOut && !faceVerifier.verify(userId, request.faceImageBase64)) {
            logAttempt(tenantId, userId, AttendanceType.CLOCK_OUT, AttemptReason.FACE_MISMATCH, request.latitude, request.longitude, request.deviceId)
            throw DomainException(ErrorCode.FACE_MISMATCH)
        }

        record.clockOutAt = now.toInstant()
        record.clockOutLatitude = request.latitude
        record.clockOutLongitude = request.longitude
        record.clockOutDeviceId = deviceResult.device.deviceId
        record.clockOutLocationId = matchedLocation?.id
        record.clockOutOutsideRadius = matchedLocation == null
        record.workDurationMinutes = computeDurationMinutes(record.clockInAt, record.clockOutAt)
        record.status = resolveFinalStatus(record, schedule)

        val saved = records.save(record)

        if (deviceResult.registered) {
            deviceBinding.auditRegistered(tenantId, userId, deviceResult.device)
        }

        audit.log(
            tenantId = tenantId,
            actorUserId = userId,
            action = "ATTENDANCE_CLOCK_OUT",
            resourceType = "AttendanceRecord",
            resourceId = saved.id.toString(),
            metadata = mapOf(
                "status" to saved.status.name,
                "durationMinutes" to saved.workDurationMinutes,
                "outsideRadius" to saved.clockOutOutsideRadius,
            ),
        )

        publisher.publishEvent(
            ClockOutOccurred(
                tenantId = tenantId,
                userId = userId,
                recordId = saved.id!!,
                status = saved.status,
                workDurationMinutes = saved.workDurationMinutes,
                occurredAt = saved.clockOutAt!!,
            )
        )
        return saved
    }

    fun todayFor(tenantId: UUID, userId: UUID): AttendanceRecord? {
        val settings = settingsService.get(tenantId)
        val now = TimeUtils.nowAt(settings.timezone)
        val schedule = shiftScheduleResolver.resolve(tenantId, userId, settings, now)
        return records.findByTenantIdAndUserIdAndDate(tenantId, userId, schedule.attendanceDate)
    }

    fun history(tenantId: UUID, userId: UUID, from: LocalDate, to: LocalDate): List<AttendanceRecord> =
        records.findAllByTenantIdAndUserIdAndDateBetweenOrderByDateDesc(tenantId, userId, from, to)

    fun listAttempts(tenantId: UUID, userId: UUID?, from: Instant, to: Instant): List<AttendanceAttemptView> {
        val attemptRows = if (userId != null) {
            attempts.findAllByTenantIdAndUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, userId, from, to)
        } else {
            attempts.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, from, to)
        }
        val usersById = users.findAllById(attemptRows.map { it.userId }.distinct()).associateBy { it.id }
        return attemptRows.map { it.toView(usersById[it.userId]) }
    }

    private fun logAttempt(
        tenantId: UUID,
        userId: UUID,
        type: AttendanceType,
        reason: AttemptReason,
        latitude: Double?,
        longitude: Double?,
        deviceId: String?,
    ) {
        attemptLogger.log(tenantId, userId, type, reason, latitude, longitude, deviceId)
    }

    private fun resolveFinalStatus(
        record: AttendanceRecord,
        schedule: ResolvedShiftSchedule,
    ): AttendanceStatus {
        val clockOutZdt = record.clockOutAt?.atZone(TimeUtils.zone(schedule.timezone)) ?: return record.status
        return if (schedule.isBeforeScheduledEnd(clockOutZdt)) {
            AttendanceStatus.EARLY_LEAVE
        } else {
            AttendanceStatus.COMPLETED
        }
    }

    private fun computeDurationMinutes(start: Instant?, end: Instant?): Int? {
        if (start == null || end == null) return null
        return ((end.toEpochMilli() - start.toEpochMilli()) / 60_000).toInt()
    }
}

fun AttendanceRecord.toView(): AttendanceRecordView = AttendanceRecordView(
    id = id ?: error("record id null"),
    tenantId = tenantId,
    userId = userId,
    date = date,
    clockInAt = clockInAt,
    clockOutAt = clockOutAt,
    clockInLocationId = clockInLocationId,
    clockOutLocationId = clockOutLocationId,
    clockInLatitude = clockInLatitude,
    clockInLongitude = clockInLongitude,
    clockOutLatitude = clockOutLatitude,
    clockOutLongitude = clockOutLongitude,
    clockInDeviceId = clockInDeviceId,
    clockOutDeviceId = clockOutDeviceId,
    clockOutOutsideRadius = clockOutOutsideRadius,
    status = status,
    workDurationMinutes = workDurationMinutes,
    shiftId = shiftTemplateId,
    shiftName = shiftName,
    scheduledStartTime = scheduledStartTime,
    scheduledEndTime = scheduledEndTime,
    lateThresholdMinutes = lateThresholdMinutes,
)

fun AttendanceAttempt.toView(user: User? = null): AttendanceAttemptView = AttendanceAttemptView(
    attemptId = id ?: error("attempt id null"),
    userId = userId,
    fullName = user?.fullName,
    email = user?.email,
    type = type,
    reason = reason,
    latitude = latitude,
    longitude = longitude,
    deviceId = deviceId,
    createdAt = createdAt,
)
