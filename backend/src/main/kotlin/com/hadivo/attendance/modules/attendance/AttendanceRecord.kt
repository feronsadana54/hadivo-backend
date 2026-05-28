package com.hadivo.attendance.modules.attendance

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
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "attendance_records")
class AttendanceRecord(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(nullable = false)
    var date: LocalDate,

    @Column(name = "clock_in_at")
    var clockInAt: Instant? = null,

    @Column(name = "clock_out_at")
    var clockOutAt: Instant? = null,

    @Column(name = "clock_in_location_id")
    var clockInLocationId: UUID? = null,

    @Column(name = "clock_out_location_id")
    var clockOutLocationId: UUID? = null,

    @Column(name = "clock_in_latitude")
    var clockInLatitude: Double? = null,

    @Column(name = "clock_in_longitude")
    var clockInLongitude: Double? = null,

    @Column(name = "clock_out_latitude")
    var clockOutLatitude: Double? = null,

    @Column(name = "clock_out_longitude")
    var clockOutLongitude: Double? = null,

    @Column(name = "clock_in_device_id")
    var clockInDeviceId: String? = null,

    @Column(name = "clock_out_device_id")
    var clockOutDeviceId: String? = null,

    @Column(name = "clock_out_outside_radius", nullable = false)
    var clockOutOutsideRadius: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AttendanceStatus,

    @Column(name = "work_duration_minutes")
    var workDurationMinutes: Int? = null,

    @Column(name = "shift_template_id")
    var shiftTemplateId: UUID? = null,

    @Column(name = "shift_name", length = 120)
    var shiftName: String? = null,

    @Column(name = "scheduled_start_time")
    var scheduledStartTime: LocalTime? = null,

    @Column(name = "scheduled_end_time")
    var scheduledEndTime: LocalTime? = null,

    @Column(name = "late_threshold_minutes")
    var lateThresholdMinutes: Int? = null,
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
