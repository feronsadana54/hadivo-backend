package com.hadivo.attendance.modules.leave.balance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LeaveBalanceLedgerRepository : JpaRepository<LeaveBalanceLedger, UUID> {

    fun existsByLeaveRequestIdAndChangeType(
        leaveRequestId: UUID,
        changeType: LeaveBalanceChangeType,
    ): Boolean

    fun findAllByTenantIdAndUserIdAndYearOrderByCreatedAtDesc(
        tenantId: UUID,
        userId: UUID,
        year: Int,
    ): List<LeaveBalanceLedger>
}
