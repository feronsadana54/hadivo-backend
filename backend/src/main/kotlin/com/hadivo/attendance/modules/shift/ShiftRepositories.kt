package com.hadivo.attendance.modules.shift

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface ShiftTemplateRepository : JpaRepository<ShiftTemplate, UUID> {
    fun findAllByTenantIdOrderByActiveDescStartTimeAscNameAsc(tenantId: UUID): List<ShiftTemplate>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): ShiftTemplate?
}

@Repository
interface MemberShiftAssignmentRepository : JpaRepository<MemberShiftAssignment, UUID> {
    fun findAllByTenantIdAndUserIdOrderByEffectiveFromDescCreatedAtDesc(
        tenantId: UUID,
        userId: UUID,
    ): List<MemberShiftAssignment>

    fun findByIdAndTenantIdAndUserId(
        id: UUID,
        tenantId: UUID,
        userId: UUID,
    ): MemberShiftAssignment?

    @Query(
        """
        select assignment
        from MemberShiftAssignment assignment
        where assignment.tenantId = :tenantId
          and assignment.userId = :userId
          and assignment.active = true
          and assignment.effectiveFrom <= :date
          and (assignment.effectiveTo is null or assignment.effectiveTo >= :date)
        order by assignment.effectiveFrom desc, assignment.createdAt desc
        """
    )
    fun findActiveForDate(
        @Param("tenantId") tenantId: UUID,
        @Param("userId") userId: UUID,
        @Param("date") date: LocalDate,
    ): List<MemberShiftAssignment>

    @Query(
        """
        select assignment
        from MemberShiftAssignment assignment
        where assignment.tenantId = :tenantId
          and assignment.userId = :userId
          and assignment.active = true
          and (:excludeId is null or assignment.id <> :excludeId)
          and assignment.effectiveFrom <= :effectiveToLimit
          and (assignment.effectiveTo is null or assignment.effectiveTo >= :effectiveFrom)
        """
    )
    fun findActiveOverlaps(
        @Param("tenantId") tenantId: UUID,
        @Param("userId") userId: UUID,
        @Param("effectiveFrom") effectiveFrom: LocalDate,
        @Param("effectiveToLimit") effectiveToLimit: LocalDate,
        @Param("excludeId") excludeId: UUID?,
    ): List<MemberShiftAssignment>
}
