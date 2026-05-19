package com.hadivo.attendance.modules.attendance

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.util.TimeUtils
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.face.FaceVerifier
import com.hadivo.attendance.modules.geofence.GeofenceValidator
import com.hadivo.attendance.modules.location.LocationService
import com.hadivo.attendance.modules.settings.SettingsService
import com.hadivo.attendance.modules.settings.TenantAttendanceSettings
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
    private val settingsService: SettingsService,
    private val locationService: LocationService,
    private val geofence: GeofenceValidator,
    private val faceVerifier: FaceVerifier,
    private val audit: AuditLogger,
    private val publisher: ApplicationEventPublisher,
) {

    @Transactional
    fun clockIn(tenantId: UUID, userId: UUID, request: ClockInRequest): AttendanceRecord {
        val settings = settingsService.get(tenantId)
        val today = TimeUtils.todayAt(settings.timezone)
        val now = TimeUtils.nowAt(settings.timezone)

        records.findByTenantIdAndUserIdAndDate(tenantId, userId, today)?.let { existing ->
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

        val isLate = TimeUtils.isAfterWithGrace(now.toLocalTime(), settings.workStartTime, settings.lateThresholdMinutes)
        if (isLate && !settings.allowLateClockIn) {
            logAttempt(tenantId, userId, AttendanceType.CLOCK_IN, AttemptReason.LATE_NOT_ALLOWED, request.latitude, request.longitude, request.deviceId)
            throw DomainException(ErrorCode.LATE_NOT_ALLOWED)
        }

        val record = records.save(
            AttendanceRecord(
                tenantId = tenantId,
                userId = userId,
                date = today,
                clockInAt = now.toInstant(),
                clockInLocationId = location.id,
                clockInLatitude = request.latitude,
                clockInLongitude = request.longitude,
                clockInDeviceId = request.deviceId,
                status = if (isLate) AttendanceStatus.LATE else AttendanceStatus.ON_TIME,
            )
        )

        audit.log(
            tenantId = tenantId,
            actorUserId = userId,
            action = "ATTENDANCE_CLOCK_IN",
            resourceType = "AttendanceRecord",
            resourceId = record.id.toString(),
            metadata = mapOf("status" to record.status.name, "locationId" to location.id?.toString()),
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
        val today = TimeUtils.todayAt(settings.timezone)
        val now = TimeUtils.nowAt(settings.timezone)

        val record = records.findByTenantIdAndUserIdAndDate(tenantId, userId, today)
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
        record.clockOutDeviceId = request.deviceId
        record.clockOutLocationId = matchedLocation?.id
        record.clockOutOutsideRadius = matchedLocation == null
        record.workDurationMinutes = computeDurationMinutes(record.clockInAt, record.clockOutAt)
        record.status = resolveFinalStatus(record, settings, today)

        val saved = records.save(record)

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
        val timezone = settingsService.get(tenantId).timezone
        return records.findByTenantIdAndUserIdAndDate(tenantId, userId, TimeUtils.todayAt(timezone))
    }

    fun history(tenantId: UUID, userId: UUID, from: LocalDate, to: LocalDate): List<AttendanceRecord> =
        records.findAllByTenantIdAndUserIdAndDateBetweenOrderByDateDesc(tenantId, userId, from, to)

    fun listAttempts(tenantId: UUID, userId: UUID?, from: Instant, to: Instant): List<AttendanceAttempt> =
        if (userId != null) {
            attempts.findAllByTenantIdAndUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, userId, from, to)
        } else {
            attempts.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, from, to)
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
        settings: TenantAttendanceSettings,
        today: LocalDate,
    ): AttendanceStatus {
        val clockOutZdt = record.clockOutAt?.atZone(TimeUtils.zone(settings.timezone)) ?: return record.status
        return if (clockOutZdt.toLocalDate() == today && clockOutZdt.toLocalTime().isBefore(settings.workEndTime)) {
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
)

fun AttendanceAttempt.toView(): AttendanceAttemptView = AttendanceAttemptView(
    id = id ?: error("attempt id null"),
    tenantId = tenantId,
    userId = userId,
    type = type,
    reason = reason,
    latitude = latitude,
    longitude = longitude,
    deviceId = deviceId,
    createdAt = createdAt,
)
