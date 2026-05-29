package com.hadivo.attendance.modules.leave

enum class LeaveRequestType {
    SICK,
    PERMISSION,
    ANNUAL_LEAVE,
    BUSINESS_TRIP,
    ATTENDANCE_CORRECTION;

    fun isAttendanceCorrection(): Boolean = this == ATTENDANCE_CORRECTION
}
