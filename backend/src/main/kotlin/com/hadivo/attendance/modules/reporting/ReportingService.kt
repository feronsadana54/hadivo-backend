package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.modules.attendance.AttendanceRecord
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.auth.User
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.leave.LeaveRequest
import com.hadivo.attendance.modules.leave.LeaveRequestRepository
import com.hadivo.attendance.modules.leave.LeaveRequestType
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
    private val leaves: LeaveRequestRepository,
) {

    fun daily(tenantId: UUID, date: LocalDate): DailyReport {
        val rows = records.findAllByTenantIdAndDate(tenantId, date)
        val approvedLeaves = leaves.findApprovedOnDate(tenantId, date)
        val leavesByUser = approvedLeaves.groupBy { it.requesterUserId }
            .mapValues { (_, group) -> selectPrimaryLeave(group) }

        val userIds = (rows.map { it.userId } + leavesByUser.keys).distinct()
        val usersById = users.findAllById(userIds).associateBy { it.id }

        val totals = AttendanceStatus.values().associateWith { status ->
            rows.count { it.status == status }
        }.filterValues { it > 0 }
        val leaveTotals = approvedLeaves.groupingBy { it.requestType }.eachCount()

        val attendanceRows = rows.map { record ->
            buildDailyRow(record, usersById[record.userId], leavesByUser[record.userId])
        }
        val leaveOnlyRows = leavesByUser
            .filterKeys { userId -> rows.none { it.userId == userId } }
            .map { (userId, leave) -> buildLeaveOnlyDailyRow(userId, usersById[userId], leave) }

        return DailyReport(
            date = date,
            tenantId = tenantId,
            totals = totals,
            leaveTotals = leaveTotals,
            rows = attendanceRows + leaveOnlyRows,
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
        val rows = exportRows(tenantId, from, to)

        return buildString {
            appendCsvLine(AttendanceReportExportColumns.headers)

            rows.forEach { row ->
                appendCsvLine(
                    listOf(
                        row.date.toString(),
                        row.userId.toString(),
                        row.fullName,
                        row.email,
                        row.status,
                        row.clockInTime,
                        row.clockOutTime,
                        row.workDurationMinutes?.toString().orEmpty(),
                        row.clockOutOutsideRadius.toString(),
                        row.shiftName,
                        row.scheduledStartTime,
                        row.scheduledEndTime,
                        row.lateThresholdMinutes?.toString().orEmpty(),
                        row.leaveType,
                        row.leaveStatus,
                    )
                )
            }
        }
    }

    fun exportRows(tenantId: UUID, from: LocalDate, to: LocalDate): List<AttendanceReportExportRow> {
        validateExportRange(from, to)

        val rows = records.findAllByTenantIdAndDateBetween(tenantId, from, to)
        val approvedLeaves = leaves.findApprovedOverlapping(tenantId, from, to)

        val leavesByUserDate: Map<UUID, Map<LocalDate, LeaveRequest>> = approvedLeaves
            .groupBy { it.requesterUserId }
            .mapValues { (_, list) ->
                val byDate = mutableMapOf<LocalDate, LeaveRequest>()
                list.forEach { leave ->
                    val rangeStart = if (leave.startDate.isBefore(from)) from else leave.startDate
                    val rangeEnd = if (leave.endDate.isAfter(to)) to else leave.endDate
                    var d = rangeStart
                    while (!d.isAfter(rangeEnd)) {
                        val existing = byDate[d]
                        if (existing == null || hasHigherPriority(leave, existing)) {
                            byDate[d] = leave
                        }
                        d = d.plusDays(1)
                    }
                }
                byDate
            }

        val attendanceUserIds = rows.map { it.userId }.toSet()
        val leaveOnlyUserIds = leavesByUserDate.keys - attendanceUserIds
        val userIds = (attendanceUserIds + leaveOnlyUserIds).toList()
        val usersById = users.findAllById(userIds)
            .mapNotNull { user -> user.id?.let { it to user } }
            .toMap()

        val attendanceRows = rows.map { record ->
            val user = usersById[record.userId]
            val leave = leavesByUserDate[record.userId]?.get(record.date)
            buildExportRow(record, user, leave)
        }

        val leaveOnlyRows = leavesByUserDate.flatMap { (userId, byDate) ->
            byDate.entries
                .filter { (date, _) -> rows.none { it.userId == userId && it.date == date } }
                .map { (date, leave) ->
                    buildLeaveOnlyExportRow(userId, usersById[userId], date, leave)
                }
        }

        return (attendanceRows + leaveOnlyRows).sortedWith(
            compareBy({ it.date }, { it.fullName }, { it.userId.toString() })
        )
    }

    private fun buildDailyRow(
        record: AttendanceRecord,
        user: User?,
        leave: LeaveRequest?,
    ): DailyReportRow = DailyReportRow(
        userId = record.userId,
        fullName = user?.fullName,
        email = user?.email,
        status = record.status,
        clockInAt = record.clockInAt,
        clockOutAt = record.clockOutAt,
        workDurationMinutes = record.workDurationMinutes,
        clockOutOutsideRadius = record.clockOutOutsideRadius,
        shiftId = record.shiftTemplateId,
        shiftName = record.shiftName,
        scheduledStartTime = record.scheduledStartTime,
        scheduledEndTime = record.scheduledEndTime,
        lateThresholdMinutes = record.lateThresholdMinutes,
        leaveRequestId = leave?.id,
        leaveType = leave?.requestType,
        leaveStatus = leave?.status,
    )

    private fun buildLeaveOnlyDailyRow(
        userId: UUID,
        user: User?,
        leave: LeaveRequest,
    ): DailyReportRow = DailyReportRow(
        userId = userId,
        fullName = user?.fullName,
        email = user?.email,
        status = null,
        clockInAt = null,
        clockOutAt = null,
        workDurationMinutes = null,
        clockOutOutsideRadius = false,
        shiftId = null,
        shiftName = null,
        scheduledStartTime = null,
        scheduledEndTime = null,
        lateThresholdMinutes = null,
        leaveRequestId = leave.id,
        leaveType = leave.requestType,
        leaveStatus = leave.status,
    )

    private fun buildExportRow(
        record: AttendanceRecord,
        user: User?,
        leave: LeaveRequest?,
    ): AttendanceReportExportRow = AttendanceReportExportRow(
        date = record.date,
        userId = record.userId,
        fullName = user?.fullName.orEmpty(),
        email = user?.email.orEmpty(),
        status = record.status.name,
        clockInTime = formatInstant(record.clockInAt),
        clockOutTime = formatInstant(record.clockOutAt),
        workDurationMinutes = record.workDurationMinutes,
        clockOutOutsideRadius = record.clockOutOutsideRadius,
        shiftName = record.shiftName.orEmpty(),
        scheduledStartTime = record.scheduledStartTime?.toString().orEmpty(),
        scheduledEndTime = record.scheduledEndTime?.toString().orEmpty(),
        lateThresholdMinutes = record.lateThresholdMinutes,
        leaveType = leave?.requestType?.name.orEmpty(),
        leaveStatus = leave?.status?.name.orEmpty(),
    )

    private fun buildLeaveOnlyExportRow(
        userId: UUID,
        user: User?,
        date: LocalDate,
        leave: LeaveRequest,
    ): AttendanceReportExportRow = AttendanceReportExportRow(
        date = date,
        userId = userId,
        fullName = user?.fullName.orEmpty(),
        email = user?.email.orEmpty(),
        status = "",
        clockInTime = "",
        clockOutTime = "",
        workDurationMinutes = null,
        clockOutOutsideRadius = false,
        shiftName = "",
        scheduledStartTime = "",
        scheduledEndTime = "",
        lateThresholdMinutes = null,
        leaveType = leave.requestType.name,
        leaveStatus = leave.status.name,
    )

    private fun selectPrimaryLeave(group: List<LeaveRequest>): LeaveRequest =
        group.minByOrNull { typePriority(it.requestType) } ?: group.first()

    private fun hasHigherPriority(candidate: LeaveRequest, existing: LeaveRequest): Boolean =
        typePriority(candidate.requestType) < typePriority(existing.requestType)

    private fun typePriority(type: LeaveRequestType): Int = when (type) {
        LeaveRequestType.SICK -> 0
        LeaveRequestType.PERMISSION -> 1
        LeaveRequestType.ANNUAL_LEAVE -> 2
        LeaveRequestType.BUSINESS_TRIP -> 3
        LeaveRequestType.ATTENDANCE_CORRECTION -> 4
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
