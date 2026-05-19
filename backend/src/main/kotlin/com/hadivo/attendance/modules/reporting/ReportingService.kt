package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Service
class ReportingService(private val records: AttendanceRecordRepository) {

    fun daily(tenantId: UUID, date: LocalDate): DailyReport {
        val rows = records.findAllByTenantIdAndDate(tenantId, date)
        val totals = AttendanceStatus.values().associateWith { status ->
            rows.count { it.status == status }
        }.filterValues { it > 0 }
        return DailyReport(
            date = date,
            tenantId = tenantId,
            totals = totals,
            rows = rows.map {
                DailyReportRow(
                    userId = it.userId,
                    status = it.status,
                    clockInAt = it.clockInAt,
                    clockOutAt = it.clockOutAt,
                    workDurationMinutes = it.workDurationMinutes,
                )
            },
        )
    }

    fun monthly(tenantId: UUID, month: YearMonth): MonthlyReport {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val all = records.findAllByTenantIdAndDateBetween(tenantId, from, to)
        val grouped = all.groupBy { it.date }
        val days = grouped.toSortedMap().map { (date, group) ->
            MonthlyReportRow(
                date = date,
                total = group.size,
                byStatus = group.groupingBy { it.status }.eachCount(),
            )
        }
        return MonthlyReport(month = month.toString(), tenantId = tenantId, days = days)
    }
}
