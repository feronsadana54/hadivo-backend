package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.leave.LeaveRequestStatus
import com.hadivo.attendance.modules.leave.LeaveRequestType
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class DailyReportRow(
    val userId: UUID,
    val fullName: String?,
    val email: String?,
    val status: AttendanceStatus?,
    val clockInAt: java.time.Instant?,
    val clockOutAt: java.time.Instant?,
    val workDurationMinutes: Int?,
    val clockOutOutsideRadius: Boolean,
    val shiftId: UUID?,
    val shiftName: String?,
    val scheduledStartTime: LocalTime?,
    val scheduledEndTime: LocalTime?,
    val lateThresholdMinutes: Int?,
    val leaveRequestId: UUID?,
    val leaveType: LeaveRequestType?,
    val leaveStatus: LeaveRequestStatus?,
)

data class DailyReport(
    val date: LocalDate,
    val tenantId: UUID,
    val totals: Map<AttendanceStatus, Int>,
    val leaveTotals: Map<LeaveRequestType, Int> = emptyMap(),
    val rows: List<DailyReportRow>,
)

data class MonthlyReportRow(
    val date: LocalDate,
    val total: Int,
    val byStatus: Map<AttendanceStatus, Int>,
)

data class MonthlyReport(
    val month: String,
    val tenantId: UUID,
    val days: List<MonthlyReportRow>,
)

data class AttendanceReportExportRow(
    val date: LocalDate,
    val userId: UUID,
    val fullName: String,
    val email: String,
    val status: String,
    val clockInTime: String,
    val clockOutTime: String,
    val workDurationMinutes: Int?,
    val clockOutOutsideRadius: Boolean,
    val shiftName: String,
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val lateThresholdMinutes: Int?,
    val leaveType: String,
    val leaveStatus: String,
)

object AttendanceReportExportColumns {
    val headers = listOf(
        "Date",
        "User ID",
        "Full Name",
        "Email",
        "Status",
        "Clock In Time",
        "Clock Out Time",
        "Work Duration Minutes",
        "Clock Out Outside Radius",
        "Shift",
        "Scheduled Start",
        "Scheduled End",
        "Late Threshold Minutes",
        "Leave Type",
        "Leave Status",
    )
}
