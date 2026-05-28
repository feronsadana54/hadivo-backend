package com.hadivo.attendance.modules.shift

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "shift_templates")
class ShiftTemplate(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(nullable = false, length = 120)
    var name: String,

    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,

    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,

    @Column(name = "late_threshold_minutes", nullable = false)
    var lateThresholdMinutes: Int = 0,

    @Column(name = "allows_overtime", nullable = false)
    var allowsOvertime: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,
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
