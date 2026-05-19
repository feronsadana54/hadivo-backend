package com.hadivo.attendance.modules.membership

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MembershipRepository : JpaRepository<Membership, UUID> {
    fun findByTenantIdAndUserId(tenantId: UUID, userId: UUID): Membership?
    fun findAllByTenantId(tenantId: UUID): List<Membership>
    fun countByTenantIdAndActive(tenantId: UUID, active: Boolean): Long
    fun existsByTenantIdAndUserIdAndActive(tenantId: UUID, userId: UUID, active: Boolean): Boolean
}
