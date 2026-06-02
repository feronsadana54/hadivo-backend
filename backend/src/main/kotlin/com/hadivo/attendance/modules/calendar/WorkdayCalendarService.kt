package com.hadivo.attendance.modules.calendar

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class WorkdayCalendarService(
    private val workdaySettingsService: WorkdaySettingsService,
    private val holidayRepository: TenantHolidayRepository,
) {

    @Transactional
    fun countWorkdays(tenantId: UUID, start: LocalDate, end: LocalDate): Int {
        if (end.isBefore(start)) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal akhir tidak boleh sebelum tanggal mulai")
        }
        val span = ChronoUnit.DAYS.between(start, end) + 1
        if (span > MAX_SPAN_DAYS) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Rentang perhitungan hari kerja maksimal $MAX_SPAN_DAYS hari",
            )
        }

        val settings = workdaySettingsService.getOrCreateDefault(tenantId)
        val holidaySet = holidayRepository
            .findAllByTenantIdAndActiveTrueAndHolidayDateBetween(tenantId, start, end)
            .mapTo(HashSet()) { it.holidayDate }

        var count = 0
        var cursor = start
        while (!cursor.isAfter(end)) {
            if (settings.isWorkday(cursor.dayOfWeek) && cursor !in holidaySet) {
                count++
            }
            cursor = cursor.plusDays(1)
        }
        return count
    }

    @Transactional(readOnly = true)
    fun isWorkday(tenantId: UUID, date: LocalDate): Boolean {
        val settings = workdaySettingsService.getOrCreateDefault(tenantId)
        if (!settings.isWorkday(date.dayOfWeek)) return false
        val holidays = holidayRepository
            .findAllByTenantIdAndActiveTrueAndHolidayDateBetween(tenantId, date, date)
        return holidays.isEmpty()
    }

    companion object {
        const val MAX_SPAN_DAYS = 31L
    }
}
