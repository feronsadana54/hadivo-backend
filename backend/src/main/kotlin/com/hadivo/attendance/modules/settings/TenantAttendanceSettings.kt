package com.hadivo.attendance.modules.settings

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "tenant_attendance_settings")
class TenantAttendanceSettings(
    @Id
    @Column(name = "tenant_id")
    var tenantId: UUID,

    @Column(name = "require_face_clock_in", nullable = false)
    var requireFaceClockIn: Boolean = false,

    @Column(name = "require_face_clock_out", nullable = false)
    var requireFaceClockOut: Boolean = false,

    @Column(name = "allow_clock_out_outside_radius", nullable = false)
    var allowClockOutOutsideRadius: Boolean = false,

    @Column(name = "allow_late_clock_in", nullable = false)
    var allowLateClockIn: Boolean = true,

    @Column(name = "work_start_time", nullable = false)
    var workStartTime: LocalTime = LocalTime.of(8, 0),

    @Column(name = "work_end_time", nullable = false)
    var workEndTime: LocalTime = LocalTime.of(17, 0),

    @Column(name = "late_threshold_minutes", nullable = false)
    var lateThresholdMinutes: Int = 15,

    @Column(nullable = false)
    var timezone: String = "Asia/Jakarta",
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touchUpdatedAt() {
        updatedAt = Instant.now()
    }
}
