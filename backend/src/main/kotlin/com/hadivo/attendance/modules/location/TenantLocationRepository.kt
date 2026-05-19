package com.hadivo.attendance.modules.location

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantLocationRepository : JpaRepository<TenantLocation, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<TenantLocation>
    fun findAllByTenantIdAndActive(tenantId: UUID, active: Boolean): List<TenantLocation>
}
