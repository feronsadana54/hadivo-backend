package com.hadivo.attendance.modules.attendance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface AttendanceRecordRepository : JpaRepository<AttendanceRecord, UUID> {
    fun findByTenantIdAndUserIdAndDate(tenantId: UUID, userId: UUID, date: LocalDate): AttendanceRecord?
    fun countByTenantIdAndDate(tenantId: UUID, date: LocalDate): Long
    fun findAllByTenantIdAndUserIdAndDateBetweenOrderByDateDesc(
        tenantId: UUID,
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceRecord>
    fun findAllByTenantIdAndDate(tenantId: UUID, date: LocalDate): List<AttendanceRecord>
    fun findAllByTenantIdAndDateBetween(tenantId: UUID, from: LocalDate, to: LocalDate): List<AttendanceRecord>
}

@Repository
interface AttendanceAttemptRepository : JpaRepository<AttendanceAttempt, UUID> {
    fun countByTenantIdAndCreatedAtBetween(
        tenantId: UUID,
        from: Instant,
        to: Instant,
    ): Long
    fun findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        tenantId: UUID,
        from: Instant,
        to: Instant,
    ): List<AttendanceAttempt>
    fun findAllByTenantIdAndUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        tenantId: UUID,
        userId: UUID,
        from: Instant,
        to: Instant,
    ): List<AttendanceAttempt>
    fun findTop10ByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<AttendanceAttempt>
}
