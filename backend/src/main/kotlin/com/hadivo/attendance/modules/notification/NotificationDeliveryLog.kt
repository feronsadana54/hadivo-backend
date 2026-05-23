package com.hadivo.attendance.modules.notification

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
@Table(name = "notification_delivery_logs")
class NotificationDeliveryLog(
    @Column(name = "tenant_id")
    var tenantId: UUID?,

    @Column(name = "recipient_user_id")
    var recipientUserId: UUID?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var channel: NotificationChannel,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    var eventType: NotificationEventType,

    @Column
    var destination: String? = null,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "text")
    var body: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NotificationDeliveryStatus,

    @Column
    var provider: String? = null,

    @Column(name = "provider_message_id")
    var providerMessageId: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,

    @Column(name = "metadata_json", columnDefinition = "text")
    var metadataJson: String? = null,

    @Column(name = "sent_at")
    var sentAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

fun NotificationDeliveryLog.toResponse(): NotificationDeliveryLogResponse =
    NotificationDeliveryLogResponse(
        id = id ?: error("delivery log id null"),
        eventType = eventType,
        channel = channel,
        recipientUserId = recipientUserId,
        destination = destination,
        title = title,
        status = status,
        provider = provider,
        createdAt = createdAt,
        sentAt = sentAt,
        errorMessage = errorMessage,
    )
