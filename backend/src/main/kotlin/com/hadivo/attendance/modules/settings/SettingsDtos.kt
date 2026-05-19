package com.hadivo.attendance.modules.settings

import java.time.LocalTime
import java.util.UUID

data class UpdateSettingsRequest(
    val requireFaceClockIn: Boolean? = null,
    val requireFaceClockOut: Boolean? = null,
    val allowClockOutOutsideRadius: Boolean? = null,
    val allowLateClockIn: Boolean? = null,
    val workStartTime: LocalTime? = null,
    val workEndTime: LocalTime? = null,
    val lateThresholdMinutes: Int? = null,
    val timezone: String? = null,
)

data class SettingsView(
    val tenantId: UUID,
    val requireFaceClockIn: Boolean,
    val requireFaceClockOut: Boolean,
    val allowClockOutOutsideRadius: Boolean,
    val allowLateClockIn: Boolean,
    val workStartTime: LocalTime,
    val workEndTime: LocalTime,
    val lateThresholdMinutes: Int,
    val timezone: String,
)
