package com.hadivo.attendance.modules.attendance

import java.time.Instant
import java.util.UUID

sealed interface AttendanceEvent {
    val tenantId: UUID
    val userId: UUID
    val occurredAt: Instant
}

data class ClockInOccurred(
    override val tenantId: UUID,
    override val userId: UUID,
    val recordId: UUID,
    val status: AttendanceStatus,
    override val occurredAt: Instant,
) : AttendanceEvent

data class ClockOutOccurred(
    override val tenantId: UUID,
    override val userId: UUID,
    val recordId: UUID,
    val status: AttendanceStatus,
    val workDurationMinutes: Int?,
    override val occurredAt: Instant,
) : AttendanceEvent

data class AttemptFailed(
    override val tenantId: UUID,
    override val userId: UUID,
    val type: AttendanceType,
    val reason: AttemptReason,
    override val occurredAt: Instant,
) : AttendanceEvent
