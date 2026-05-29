package com.hadivo.attendance.modules.leave.correction

import com.hadivo.attendance.modules.attendance.AttendanceStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "attendance_correction_applies")
class AttendanceCorrectionApply(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "leave_request_id", nullable = false, unique = true)
    var leaveRequestId: UUID,

    @Column(name = "attendance_record_id")
    var attendanceRecordId: UUID? = null,

    @Column(name = "requester_user_id", nullable = false)
    var requesterUserId: UUID,

    @Column(name = "reviewer_user_id", nullable = false)
    var reviewerUserId: UUID,

    @Column(name = "applied_by", nullable = false)
    var appliedBy: UUID,

    @Column(name = "original_clock_in_at")
    var originalClockInAt: Instant? = null,

    @Column(name = "original_clock_out_at")
    var originalClockOutAt: Instant? = null,

    @Column(name = "applied_clock_in_at")
    var appliedClockInAt: Instant? = null,

    @Column(name = "applied_clock_out_at")
    var appliedClockOutAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "original_status", length = 40)
    var originalStatus: AttendanceStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_status", nullable = false, length = 40)
    var appliedStatus: AttendanceStatus,

    @Column(name = "original_work_duration_minutes")
    var originalWorkDurationMinutes: Int? = null,

    @Column(name = "applied_work_duration_minutes")
    var appliedWorkDurationMinutes: Int? = null,

    @Column(name = "correction_reason")
    var correctionReason: String? = null,

    @Column(name = "record_created_by_correction", nullable = false)
    var recordCreatedByCorrection: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "applied_at", nullable = false, updatable = false)
    var appliedAt: Instant = Instant.now()

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
