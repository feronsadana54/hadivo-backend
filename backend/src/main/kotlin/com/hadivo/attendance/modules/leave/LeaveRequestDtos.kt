package com.hadivo.attendance.modules.leave

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateLeaveRequestRequest(
    val requestType: LeaveRequestType?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val reason: String? = null,
    val requestedClockInTime: Instant? = null,
    val requestedClockOutTime: Instant? = null,
    val correctionNote: String? = null,
)

data class ReviewLeaveRequestRequest(
    val reviewNote: String? = null,
)

data class LeaveRequestView(
    val id: UUID,
    val tenantId: UUID,
    val requesterUserId: UUID,
    val requesterFullName: String?,
    val requesterEmail: String?,
    val requestType: LeaveRequestType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String?,
    val status: LeaveRequestStatus,
    val reviewerUserId: UUID?,
    val reviewerFullName: String?,
    val reviewedAt: Instant?,
    val reviewNote: String?,
    val attachmentUrl: String?,
    val requestedClockInTime: Instant?,
    val requestedClockOutTime: Instant?,
    val correctionNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
