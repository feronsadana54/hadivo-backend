package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReportingService(
    private val records: AttendanceRecordRepository,
    private val users: UserRepository,
) {

    fun daily(tenantId: UUID, date: LocalDate): DailyReport {
        val rows = records.findAllByTenantIdAndDate(tenantId, date)
        val usersById = users.findAllById(rows.map { it.userId }.distinct()).associateBy { it.id }
        val totals = AttendanceStatus.values().associateWith { status ->
            rows.count { it.status == status }
        }.filterValues { it > 0 }
        return DailyReport(
            date = date,
            tenantId = tenantId,
            totals = totals,
            rows = rows.map {
                val user = usersById[it.userId]
                DailyReportRow(
                    userId = it.userId,
                    fullName = user?.fullName,
                    email = user?.email,
                    status = it.status,
                    clockInAt = it.clockInAt,
                    clockOutAt = it.clockOutAt,
                    workDurationMinutes = it.workDurationMinutes,
                    clockOutOutsideRadius = it.clockOutOutsideRadius,
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

    fun exportCsv(tenantId: UUID, from: LocalDate, to: LocalDate): String {
        validateExportRange(from, to)

        val rows = records.findAllByTenantIdAndDateBetween(tenantId, from, to)
        val usersById = users.findAllById(rows.map { it.userId }.distinct())
            .mapNotNull { user -> user.id?.let { it to user } }
            .toMap()

        return buildString {
            appendCsvLine(
                listOf(
                    "Date",
                    "User ID",
                    "Full Name",
                    "Email",
                    "Status",
                    "Clock In Time",
                    "Clock Out Time",
                    "Work Duration Minutes",
                    "Clock Out Outside Radius",
                )
            )

            rows
                .sortedWith(compareBy({ it.date }, { usersById[it.userId]?.fullName ?: "" }, { it.userId.toString() }))
                .forEach { record ->
                    val user = usersById[record.userId]
                    appendCsvLine(
                        listOf(
                            record.date.toString(),
                            record.userId.toString(),
                            user?.fullName.orEmpty(),
                            user?.email.orEmpty(),
                            record.status.name,
                            formatInstant(record.clockInAt),
                            formatInstant(record.clockOutAt),
                            record.workDurationMinutes?.toString().orEmpty(),
                            record.clockOutOutsideRadius.toString(),
                        )
                    )
                }
        }
    }

    private fun validateExportRange(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Tanggal awal tidak boleh setelah tanggal akhir",
                mapOf("from" to from, "to" to to),
            )
        }

        val days = ChronoUnit.DAYS.between(from, to) + 1
        if (days > MAX_EXPORT_RANGE_DAYS) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Range export attendance maksimal $MAX_EXPORT_RANGE_DAYS hari",
                mapOf("from" to from, "to" to to, "maxDays" to MAX_EXPORT_RANGE_DAYS),
            )
        }
    }

    private fun StringBuilder.appendCsvLine(values: List<String>) {
        append(values.joinToString(",") { escapeCsv(it) })
        append("\r\n")
    }

    private fun escapeCsv(value: String): String {
        val shouldQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!shouldQuote) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun formatInstant(value: Instant?): String =
        value?.let { EXPORT_TIME_FORMATTER.format(it) }.orEmpty()

    private companion object {
        const val MAX_EXPORT_RANGE_DAYS = 31L
        val EXPORT_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    }
}
