package com.hadivo.attendance.modules.device

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_devices")
class UserDevice(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "device_id", nullable = false)
    var deviceId: String,

    @Column(name = "device_name")
    var deviceName: String? = null,

    @Column
    var platform: String? = null,

    @Column(nullable = false)
    var trusted: Boolean = true,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "first_seen_at", nullable = false)
    var firstSeenAt: Instant,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant,
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
