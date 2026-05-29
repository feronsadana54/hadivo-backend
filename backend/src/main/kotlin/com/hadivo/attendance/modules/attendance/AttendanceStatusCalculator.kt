package com.hadivo.attendance.modules.attendance

import com.hadivo.attendance.common.util.TimeUtils
import com.hadivo.attendance.modules.shift.ResolvedShiftSchedule
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AttendanceStatusCalculator {

    fun resolve(
        schedule: ResolvedShiftSchedule,
        clockInAt: Instant?,
        clockOutAt: Instant?,
        currentStatus: AttendanceStatus,
    ): AttendanceStatus {
        if (clockInAt == null && clockOutAt == null) return currentStatus

        val zone = TimeUtils.zone(schedule.timezone)
        val clockInZdt = clockInAt?.atZone(zone)
        val clockOutZdt = clockOutAt?.atZone(zone)
        val late = clockInZdt?.let { schedule.isLate(it) } ?: false

        return when {
            clockInZdt != null && clockOutZdt != null -> {
                if (schedule.isBeforeScheduledEnd(clockOutZdt)) AttendanceStatus.EARLY_LEAVE
                else AttendanceStatus.COMPLETED
            }
            clockInZdt != null -> if (late) AttendanceStatus.LATE else AttendanceStatus.ON_TIME
            else -> currentStatus
        }
    }

    fun durationMinutes(clockInAt: Instant?, clockOutAt: Instant?): Int? {
        if (clockInAt == null || clockOutAt == null) return null
        return ((clockOutAt.toEpochMilli() - clockInAt.toEpochMilli()) / 60_000).toInt()
    }
}
