package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.modules.attendance.AttendanceStatus
import java.time.LocalDate
import java.util.UUID

data class DailyReportRow(
    val userId: UUID,
    val status: AttendanceStatus,
    val clockInAt: java.time.Instant?,
    val clockOutAt: java.time.Instant?,
    val workDurationMinutes: Int?,
)

data class DailyReport(
    val date: LocalDate,
    val tenantId: UUID,
    val totals: Map<AttendanceStatus, Int>,
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
