package com.hadivo.attendance.modules.settings

import com.hadivo.attendance.common.exception.DomainException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SettingsService(private val repository: TenantAttendanceSettingsRepository) {

    fun get(tenantId: UUID): TenantAttendanceSettings =
        repository.findById(tenantId)
            .orElseThrow { DomainException.notFound("Attendance settings", tenantId) }

    @Transactional
    fun update(tenantId: UUID, request: UpdateSettingsRequest): TenantAttendanceSettings {
        val settings = get(tenantId)
        request.requireFaceClockIn?.let { settings.requireFaceClockIn = it }
        request.requireFaceClockOut?.let { settings.requireFaceClockOut = it }
        request.allowClockOutOutsideRadius?.let { settings.allowClockOutOutsideRadius = it }
        request.allowLateClockIn?.let { settings.allowLateClockIn = it }
        request.workStartTime?.let { settings.workStartTime = it }
        request.workEndTime?.let { settings.workEndTime = it }
        request.lateThresholdMinutes?.let { settings.lateThresholdMinutes = it }
        request.timezone?.let { settings.timezone = it }
        return repository.save(settings)
    }
}

fun TenantAttendanceSettings.toView(): SettingsView = SettingsView(
    tenantId = tenantId,
    requireFaceClockIn = requireFaceClockIn,
    requireFaceClockOut = requireFaceClockOut,
    allowClockOutOutsideRadius = allowClockOutOutsideRadius,
    allowLateClockIn = allowLateClockIn,
    workStartTime = workStartTime,
    workEndTime = workEndTime,
    lateThresholdMinutes = lateThresholdMinutes,
    timezone = timezone,
)
