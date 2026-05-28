package com.hadivo.attendance.modules.shift

import com.hadivo.attendance.common.util.TimeUtils
import com.hadivo.attendance.modules.settings.TenantAttendanceSettings
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

@Service
class ShiftScheduleResolver(
    private val assignments: MemberShiftAssignmentRepository,
    private val shifts: ShiftTemplateRepository,
) {

    @Transactional(readOnly = true)
    fun resolve(
        tenantId: UUID,
        userId: UUID,
        settings: TenantAttendanceSettings,
        now: ZonedDateTime = TimeUtils.nowAt(settings.timezone),
    ): ResolvedShiftSchedule {
        val localDate = now.toLocalDate()
        val previousDate = localDate.minusDays(1)
        val previousSchedule = assignedScheduleFor(tenantId, userId, settings.timezone, previousDate)
        if (previousSchedule != null && previousSchedule.overnight && !now.toLocalTime().isAfter(previousSchedule.endTime)) {
            return previousSchedule
        }

        return assignedScheduleFor(tenantId, userId, settings.timezone, localDate)
            ?: fallbackSchedule(settings, localDate)
    }

    private fun assignedScheduleFor(
        tenantId: UUID,
        userId: UUID,
        timezone: String,
        date: LocalDate,
    ): ResolvedShiftSchedule? {
        val assignment = assignments.findActiveForDate(tenantId, userId, date).firstOrNull() ?: return null
        val shift = shifts.findByIdAndTenantId(assignment.shiftTemplateId, tenantId) ?: return null
        return ResolvedShiftSchedule(
            attendanceDate = date,
            timezone = timezone,
            shiftId = shift.id,
            shiftName = shift.name,
            startTime = shift.startTime,
            endTime = shift.endTime,
            lateThresholdMinutes = shift.lateThresholdMinutes,
        )
    }

    private fun fallbackSchedule(settings: TenantAttendanceSettings, date: LocalDate): ResolvedShiftSchedule =
        ResolvedShiftSchedule(
            attendanceDate = date,
            timezone = settings.timezone,
            shiftId = null,
            shiftName = null,
            startTime = settings.workStartTime,
            endTime = settings.workEndTime,
            lateThresholdMinutes = settings.lateThresholdMinutes,
        )
}

data class ResolvedShiftSchedule(
    val attendanceDate: LocalDate,
    val timezone: String,
    val shiftId: UUID?,
    val shiftName: String?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val lateThresholdMinutes: Int,
) {
    val overnight: Boolean
        get() = !endTime.isAfter(startTime)

    fun isLate(now: ZonedDateTime): Boolean =
        now.isAfter(startDateTime().plusMinutes(lateThresholdMinutes.toLong()))

    fun isBeforeScheduledEnd(value: ZonedDateTime): Boolean =
        value.toInstant().isBefore(endDateTime().toInstant())

    private fun startDateTime(): ZonedDateTime =
        attendanceDate.atTime(startTime).atZone(TimeUtils.zone(timezone))

    private fun endDateTime(): ZonedDateTime {
        val endDate = if (overnight) attendanceDate.plusDays(1) else attendanceDate
        return endDate.atTime(endTime).atZone(TimeUtils.zone(timezone))
    }
}
