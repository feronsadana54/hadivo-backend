package com.hadivo.attendance.modules.calendar

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantWorkdaySettingsRepository : JpaRepository<TenantWorkdaySettings, UUID> {
    fun findByTenantId(tenantId: UUID): TenantWorkdaySettings?
}
