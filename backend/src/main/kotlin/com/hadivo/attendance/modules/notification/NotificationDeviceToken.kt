package com.hadivo.attendance.modules.notification

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
@Table(name = "notification_device_tokens")
class NotificationDeviceToken(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "device_id")
    var deviceId: String? = null,

    @Column(name = "fcm_token", nullable = false, columnDefinition = "text")
    var fcmToken: String,

    @Column
    var platform: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),
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

fun NotificationDeviceToken.toResponse(): NotificationDeviceTokenResponse =
    NotificationDeviceTokenResponse(
        id = id ?: error("notification device token id null"),
        platform = platform,
        active = active,
        lastSeenAt = lastSeenAt,
    )
