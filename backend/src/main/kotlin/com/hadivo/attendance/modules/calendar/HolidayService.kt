package com.hadivo.attendance.modules.calendar

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class HolidayService(
    private val repository: TenantHolidayRepository,
) {

    @Transactional(readOnly = true)
    fun list(tenantId: UUID, from: LocalDate?, to: LocalDate?): List<TenantHoliday> {
        val today = LocalDate.now()
        val resolvedFrom = from ?: today.minusDays(90)
        val resolvedTo = to ?: today.plusDays(365)
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal mulai tidak boleh setelah tanggal akhir")
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > MAX_RANGE_DAYS) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Rentang pencarian hari libur maksimal ${MAX_RANGE_DAYS} hari",
            )
        }
        return repository.findAllByTenantIdAndHolidayDateBetweenOrderByHolidayDateAsc(
            tenantId, resolvedFrom, resolvedTo,
        )
    }

    @Transactional
    fun create(tenantId: UUID, request: CreateHolidayRequest): TenantHoliday {
        val date = request.holidayDate
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal hari libur wajib diisi")
        val name = request.name?.trim()?.takeIf { it.isNotBlank() }
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Nama hari libur wajib diisi")
        val type = resolveType(request.type)

        val entity = TenantHoliday(
            tenantId = tenantId,
            holidayDate = date,
            name = name,
            type = type,
            active = request.active ?: true,
        )
        return try {
            repository.saveAndFlush(entity)
        } catch (ex: DataIntegrityViolationException) {
            throw DomainException.conflict("Hari libur dengan tanggal dan nama yang sama sudah ada")
        }
    }

    @Transactional
    fun update(
        tenantId: UUID,
        holidayId: UUID,
        request: UpdateHolidayRequest,
    ): Pair<TenantHoliday, List<String>> {
        val holiday = repository.findByIdAndTenantId(holidayId, tenantId)
            ?: throw DomainException.notFound("Holiday", holidayId)
        val changed = mutableListOf<String>()

        request.holidayDate?.let {
            if (holiday.holidayDate != it) { holiday.holidayDate = it; changed.add("holidayDate") }
        }
        request.name?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (holiday.name != it) { holiday.name = it; changed.add("name") }
        }
        request.type?.let { raw ->
            val resolved = resolveType(raw)
            if (holiday.type != resolved) { holiday.type = resolved; changed.add("type") }
        }
        request.active?.let {
            if (holiday.active != it) { holiday.active = it; changed.add("active") }
        }

        val saved = try {
            repository.saveAndFlush(holiday)
        } catch (ex: DataIntegrityViolationException) {
            throw DomainException.conflict("Hari libur dengan tanggal dan nama yang sama sudah ada")
        }
        return saved to changed
    }

    private fun resolveType(raw: String?): HolidayType =
        if (raw == null) HolidayType.CUSTOM
        else HolidayType.parse(raw)
            ?: throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Tipe hari libur tidak valid. Pilihan: CUSTOM, NATIONAL, COMPANY, SCHOOL",
            )

    companion object {
        const val MAX_RANGE_DAYS = 366L
    }
}

fun TenantHoliday.toView(): HolidayView = HolidayView(
    id = id ?: error("holiday id null"),
    tenantId = tenantId,
    holidayDate = holidayDate,
    name = name,
    type = type,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
