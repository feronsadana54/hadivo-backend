package com.hadivo.attendance.modules.calendar

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface TenantHolidayRepository : JpaRepository<TenantHoliday, UUID> {

    fun findByIdAndTenantId(id: UUID, tenantId: UUID): TenantHoliday?

    fun findAllByTenantIdAndHolidayDateBetweenOrderByHolidayDateAsc(
        tenantId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<TenantHoliday>

    fun findAllByTenantIdAndActiveTrueAndHolidayDateBetween(
        tenantId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<TenantHoliday>
}
