package com.hadivo.attendance.modules.leave.balance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LeaveBalanceRepository : JpaRepository<LeaveBalance, UUID> {

    fun findByTenantIdAndUserIdAndYear(tenantId: UUID, userId: UUID, year: Int): LeaveBalance?

    @Query(
        """
        select b from LeaveBalance b
        where b.tenantId = :tenantId and b.year = :year
        order by b.userId asc
        """
    )
    fun findAllByTenantIdAndYear(
        @Param("tenantId") tenantId: UUID,
        @Param("year") year: Int,
    ): List<LeaveBalance>
}
