package com.hadivo.attendance.modules.leave.balance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LeavePolicyRepository : JpaRepository<LeavePolicy, UUID> {
    fun findByTenantId(tenantId: UUID): LeavePolicy?
}
