package com.hadivo.attendance.modules.tenant

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun existsBySlug(slug: String): Boolean
}
