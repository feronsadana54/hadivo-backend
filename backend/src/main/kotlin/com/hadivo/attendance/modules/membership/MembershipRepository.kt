package com.hadivo.attendance.modules.membership

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MembershipRepository : JpaRepository<Membership, UUID> {
    fun findByTenantIdAndUserId(tenantId: UUID, userId: UUID): Membership?
    fun findAllByTenantId(tenantId: UUID): List<Membership>
    fun countByTenantId(tenantId: UUID): Long
    fun countByTenantIdAndActive(tenantId: UUID, active: Boolean): Long
    fun existsByTenantIdAndUserIdAndActive(tenantId: UUID, userId: UUID, active: Boolean): Boolean
    fun existsByUserIdAndRoleAndActive(userId: UUID, role: Role, active: Boolean): Boolean

    @Query(
        """
        select new com.hadivo.attendance.modules.membership.MembershipResponse(
            m.id,
            u.id,
            u.fullName,
            u.email,
            u.phone,
            m.role,
            m.active,
            m.createdAt
        )
        from Membership m
        join User u on u.id = m.userId
        where m.tenantId = :tenantId
        order by u.fullName asc, u.email asc
        """
    )
    fun findResponsesByTenantId(@Param("tenantId") tenantId: UUID): List<MembershipResponse>
}
