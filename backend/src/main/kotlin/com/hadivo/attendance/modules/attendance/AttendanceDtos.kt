package com.hadivo.attendance.modules.attendance

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ClockInRequest(
    @field:Min(-90) @field:Max(90) val latitude: Double,
    @field:Min(-180) @field:Max(180) val longitude: Double,
    val deviceId: String?,
    val deviceName: String? = null,
    val platform: String? = null,
    val faceImageBase64: String? = null,
)

data class ClockOutRequest(
    @field:Min(-90) @field:Max(90) val latitude: Double,
    @field:Min(-180) @field:Max(180) val longitude: Double,
    val deviceId: String?,
    val deviceName: String? = null,
    val platform: String? = null,
    val faceImageBase64: String? = null,
)

data class AttendanceRecordView(
    val id: UUID,
    val tenantId: UUID,
    val userId: UUID,
    val date: LocalDate,
    val clockInAt: Instant?,
    val clockOutAt: Instant?,
    val clockInLocationId: UUID?,
    val clockOutLocationId: UUID?,
    val clockInLatitude: Double?,
    val clockInLongitude: Double?,
    val clockOutLatitude: Double?,
    val clockOutLongitude: Double?,
    val clockInDeviceId: String?,
    val clockOutDeviceId: String?,
    val clockOutOutsideRadius: Boolean,
    val status: AttendanceStatus,
    val workDurationMinutes: Int?,
    val shiftId: UUID?,
    val shiftName: String?,
    val scheduledStartTime: LocalTime?,
    val scheduledEndTime: LocalTime?,
    val lateThresholdMinutes: Int?,
)

data class AttendanceAttemptView(
    val attemptId: UUID,
    val userId: UUID,
    val fullName: String?,
    val email: String?,
    val type: AttendanceType,
    val reason: AttemptReason,
    val latitude: Double?,
    val longitude: Double?,
    val deviceId: String?,
    val createdAt: Instant,
)
