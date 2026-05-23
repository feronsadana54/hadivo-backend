package com.hadivo.attendance.modules.notification

import java.time.Instant
import java.util.UUID

data class NotificationRequest(
    val eventType: NotificationEventType,
    val tenantId: UUID?,
    val actorUserId: UUID?,
    val recipientUserIds: List<UUID> = emptyList(),
    val occurredAt: Instant,
    val metadata: Map<String, Any?> = emptyMap(),
)

data class NotificationRecipient(
    val userId: UUID?,
    val email: String?,
)

data class NotificationTemplate(
    val title: String,
    val body: String,
    val channels: List<NotificationChannel>,
)

data class NotificationGatewayResult(
    val status: NotificationDeliveryStatus,
    val provider: String,
    val providerMessageId: String? = null,
    val errorMessage: String? = null,
)

data class NotificationDeliveryLogResponse(
    val id: UUID,
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val recipientUserId: UUID?,
    val destination: String?,
    val title: String,
    val status: NotificationDeliveryStatus,
    val provider: String?,
    val createdAt: Instant,
    val sentAt: Instant?,
    val errorMessage: String?,
)
