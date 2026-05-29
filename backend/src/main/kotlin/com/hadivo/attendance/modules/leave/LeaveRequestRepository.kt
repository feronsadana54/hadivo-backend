package com.hadivo.attendance.modules.leave

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface LeaveRequestRepository : JpaRepository<LeaveRequest, UUID> {

    fun findByIdAndTenantId(id: UUID, tenantId: UUID): LeaveRequest?

    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<LeaveRequest>

    fun findAllByTenantIdAndRequesterUserIdOrderByCreatedAtDesc(
        tenantId: UUID,
        requesterUserId: UUID,
    ): List<LeaveRequest>

    @Query(
        """
        select r from LeaveRequest r
        where r.tenantId = :tenantId
          and r.requesterUserId = :requesterUserId
          and r.requestType <> com.hadivo.attendance.modules.leave.LeaveRequestType.ATTENDANCE_CORRECTION
          and r.status in (
            com.hadivo.attendance.modules.leave.LeaveRequestStatus.PENDING,
            com.hadivo.attendance.modules.leave.LeaveRequestStatus.APPROVED
          )
          and r.startDate <= :endDate
          and r.endDate >= :startDate
          and (:excludeId is null or r.id <> :excludeId)
        """
    )
    fun findActiveOverlaps(
        @Param("tenantId") tenantId: UUID,
        @Param("requesterUserId") requesterUserId: UUID,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("excludeId") excludeId: UUID?,
    ): List<LeaveRequest>

    @Query(
        """
        select r from LeaveRequest r
        where r.tenantId = :tenantId
          and r.status = com.hadivo.attendance.modules.leave.LeaveRequestStatus.APPROVED
          and r.startDate <= :date
          and r.endDate >= :date
        """
    )
    fun findApprovedOnDate(
        @Param("tenantId") tenantId: UUID,
        @Param("date") date: LocalDate,
    ): List<LeaveRequest>

    @Query(
        """
        select r from LeaveRequest r
        where r.tenantId = :tenantId
          and r.status = com.hadivo.attendance.modules.leave.LeaveRequestStatus.APPROVED
          and r.startDate <= :to
          and r.endDate >= :from
        """
    )
    fun findApprovedOverlapping(
        @Param("tenantId") tenantId: UUID,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<LeaveRequest>
}
