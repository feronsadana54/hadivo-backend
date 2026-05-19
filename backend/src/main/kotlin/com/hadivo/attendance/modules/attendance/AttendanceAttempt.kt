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
import java.util.UUID

@Entity
@Table(name = "attendance_attempts")
class AttendanceAttempt(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AttendanceType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reason: AttemptReason,

    @Column
    var latitude: Double? = null,

    @Column
    var longitude: Double? = null,

    @Column(name = "device_id")
    var deviceId: String? = null,
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
