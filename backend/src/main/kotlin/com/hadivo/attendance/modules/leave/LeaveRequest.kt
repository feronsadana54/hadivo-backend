package com.hadivo.attendance.modules.leave

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "leave_requests")
class LeaveRequest(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "requester_user_id", nullable = false)
    var requesterUserId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 40)
    var requestType: LeaveRequestType,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,

    @Column(name = "reason")
    var reason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: LeaveRequestStatus = LeaveRequestStatus.PENDING,

    @Column(name = "reviewer_user_id")
    var reviewerUserId: UUID? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "review_note")
    var reviewNote: String? = null,

    @Column(name = "attachment_url")
    var attachmentUrl: String? = null,

    @Column(name = "requested_clock_in_at")
    var requestedClockInAt: Instant? = null,

    @Column(name = "requested_clock_out_at")
    var requestedClockOutAt: Instant? = null,

    @Column(name = "correction_note")
    var correctionNote: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touchUpdatedAt() {
        updatedAt = Instant.now()
    }
}
