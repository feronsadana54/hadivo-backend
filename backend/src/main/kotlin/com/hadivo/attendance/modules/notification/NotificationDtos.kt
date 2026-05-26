package com.hadivo.attendance.modules.notification

import jakarta.validation.constraints.NotBlank
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
    val pushTokens: List<String> = emptyList(),
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

data class RegisterNotificationTokenRequest(
    val deviceId: String? = null,
    @field:NotBlank
    val fcmToken: String,
    val platform: String? = null,
)

data class NotificationDeviceTokenResponse(
    val id: UUID,
    val platform: String?,
    val active: Boolean,
    val lastSeenAt: Instant,
)
