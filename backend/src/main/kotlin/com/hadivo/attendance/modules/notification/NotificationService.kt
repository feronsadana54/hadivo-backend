package com.hadivo.attendance.modules.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.hadivo.attendance.common.response.PageResponse
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.parentlink.ParentLinkService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NotificationService(
    private val deliveries: NotificationDeliveryLogRepository,
    private val notifications: NotificationRepository,
    private val memberships: MembershipRepository,
    private val parentLinks: ParentLinkService,
    private val users: UserRepository,
    private val deviceTokens: NotificationDeviceTokenRepository,
    private val templates: NotificationTemplateRegistry,
    private val gateways: List<NotificationGateway>,
    private val audit: AuditLogger,
    private val mapper: ObjectMapper,
) {

    private val gatewayByChannel = gateways.associateBy { it.channel }

    @Transactional
    fun process(request: NotificationRequest) {
        val template = templates.templateFor(request.eventType)
        val recipients = resolveRecipients(request)

        recipients.forEach { recipient ->
            template.channels.forEach { channel ->
                deliver(request, recipient, template, channel)
            }
        }
    }

    @Transactional(readOnly = true)
    fun listDeliveries(
        tenantId: UUID,
        eventType: NotificationEventType?,
        channel: NotificationChannel?,
        status: NotificationDeliveryStatus?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
    ): PageResponse<NotificationDeliveryLogResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = deliveries.findAll(
            deliverySpec(
                tenantId = tenantId,
                eventType = eventType,
                channel = channel,
                status = status,
                from = from,
                to = to,
            ),
            pageable,
        )
        return PageResponse.of(result.map { it.toResponse() })
    }

    private fun resolveRecipients(request: NotificationRequest): List<NotificationRecipient> {
        val recipientIds = linkedSetOf<UUID>()
        request.recipientUserIds.forEach(recipientIds::add)
        request.actorUserId?.let(recipientIds::add)

        if (request.tenantId != null && request.actorUserId != null) {
            val membership = memberships.findByTenantIdAndUserId(request.tenantId, request.actorUserId)
            if (membership?.role == Role.STUDENT) {
                parentLinks.activeParentsOf(request.tenantId, request.actorUserId).forEach(recipientIds::add)
            }
        }

        val usersById = users.findAllById(recipientIds).associateBy { it.id }
        val pushTokensByUser = if (request.tenantId != null && recipientIds.isNotEmpty()) {
            deviceTokens.findAllByTenantIdAndUserIdInAndActiveTrue(request.tenantId, recipientIds)
                .groupBy { it.userId }
                .mapValues { (_, tokens) -> tokens.map { it.fcmToken } }
        } else {
            emptyMap()
        }
        return recipientIds.map { userId ->
            val user = usersById[userId]
            NotificationRecipient(
                userId = userId,
                email = user?.email,
                pushTokens = pushTokensByUser[userId].orEmpty(),
            )
        }.ifEmpty {
            listOf(NotificationRecipient(userId = null, email = null))
        }
    }

    private fun deliver(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
        channel: NotificationChannel,
    ) {
        val deliveryDestination = deliveryDestinationFor(channel, recipient)
        val gatewayDestination = gatewayDestinationFor(channel, recipient)
        val delivery = deliveries.save(
            NotificationDeliveryLog(
                tenantId = request.tenantId,
                recipientUserId = recipient.userId,
                channel = channel,
                eventType = request.eventType,
                destination = deliveryDestination,
                title = template.title,
                body = template.body,
                status = NotificationDeliveryStatus.PENDING,
                metadataJson = safeMetadata(request),
            )
        )

        try {
            val result = when (channel) {
                NotificationChannel.IN_APP -> deliverInApp(request, recipient, template)
                NotificationChannel.EMAIL, NotificationChannel.PUSH -> {
                    val gateway = gatewayByChannel[channel]
                    if (gateway == null) {
                        NotificationGatewayResult(
                            status = NotificationDeliveryStatus.SKIPPED,
                            provider = "none",
                            errorMessage = "No notification gateway configured",
                        )
                    } else {
                        gateway.send(request, recipient, template, gatewayDestination)
                    }
                }
            }
            applyResult(delivery, result)
        } catch (ex: Exception) {
            delivery.status = NotificationDeliveryStatus.FAILED
            delivery.provider = channel.name.lowercase()
            delivery.errorMessage = ex.message?.take(MAX_ERROR_LENGTH) ?: "Notification delivery failed"
            audit.log(
                tenantId = request.tenantId,
                actorUserId = request.actorUserId,
                action = "NOTIFICATION_FAILED",
                resourceType = "NotificationDeliveryLog",
                resourceId = delivery.id?.toString(),
                metadata = mapOf("eventType" to request.eventType.name, "channel" to channel.name),
            )
        } finally {
            deliveries.save(delivery)
        }
    }

    private fun deliverInApp(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
    ): NotificationGatewayResult {
        val recipientUserId = recipient.userId
            ?: return NotificationGatewayResult(
                status = NotificationDeliveryStatus.SKIPPED,
                provider = "in-app",
                errorMessage = "Recipient user is not available",
            )

        if (request.tenantId == null) {
            return NotificationGatewayResult(
                status = NotificationDeliveryStatus.SKIPPED,
                provider = "in-app",
                errorMessage = "Tenant is not available",
            )
        }

        val notification = notifications.save(
            Notification(
                tenantId = request.tenantId,
                recipientUserId = recipientUserId,
                type = request.eventType.name,
                payload = mapOf(
                    "title" to template.title,
                    "body" to template.body,
                    "actorUserId" to request.actorUserId?.toString(),
                    "occurredAt" to request.occurredAt.toString(),
                    "metadata" to safeMetadataMap(request),
                ),
            )
        )
        return NotificationGatewayResult(
            status = NotificationDeliveryStatus.SENT,
            provider = "in-app",
            providerMessageId = notification.id?.toString(),
        )
    }

    private fun applyResult(delivery: NotificationDeliveryLog, result: NotificationGatewayResult) {
        delivery.status = result.status
        delivery.provider = result.provider
        delivery.providerMessageId = result.providerMessageId
        delivery.errorMessage = result.errorMessage?.take(MAX_ERROR_LENGTH)
        when (result.status) {
            NotificationDeliveryStatus.SENT -> {
                delivery.sentAt = Instant.now()
                audit.log(
                    tenantId = delivery.tenantId,
                    actorUserId = null,
                    action = "NOTIFICATION_SENT",
                    resourceType = "NotificationDeliveryLog",
                    resourceId = delivery.id?.toString(),
                    metadata = mapOf("eventType" to delivery.eventType.name, "channel" to delivery.channel.name),
                )
            }

            NotificationDeliveryStatus.FAILED -> {
                audit.log(
                    tenantId = delivery.tenantId,
                    actorUserId = null,
                    action = "NOTIFICATION_FAILED",
                    resourceType = "NotificationDeliveryLog",
                    resourceId = delivery.id?.toString(),
                    metadata = mapOf("eventType" to delivery.eventType.name, "channel" to delivery.channel.name),
                )
            }

            NotificationDeliveryStatus.PENDING,
            NotificationDeliveryStatus.SKIPPED -> Unit
        }
    }

    private fun gatewayDestinationFor(channel: NotificationChannel, recipient: NotificationRecipient): String? =
        when (channel) {
            NotificationChannel.IN_APP -> recipient.userId?.toString()
            NotificationChannel.EMAIL -> recipient.email
            NotificationChannel.PUSH -> NotificationDestinationMasker.pushTokens(recipient.pushTokens)
        }

    private fun deliveryDestinationFor(channel: NotificationChannel, recipient: NotificationRecipient): String? =
        when (channel) {
            NotificationChannel.IN_APP -> recipient.userId?.toString()
            NotificationChannel.EMAIL -> NotificationDestinationMasker.email(recipient.email)
            NotificationChannel.PUSH -> NotificationDestinationMasker.pushTokens(recipient.pushTokens)
        }

    private fun safeMetadata(request: NotificationRequest): String? {
        val metadata = safeMetadataMap(request)
        if (metadata.isEmpty()) return null
        return mapper.writeValueAsString(metadata)
    }

    private fun safeMetadataMap(request: NotificationRequest): Map<String, String?> =
        request.metadata
            .filterKeys { key -> !SENSITIVE_METADATA_KEYS.any { sensitive -> key.contains(sensitive, ignoreCase = true) } }
            .mapValues { (_, value) -> value?.toString() }

    private fun deliverySpec(
        tenantId: UUID,
        eventType: NotificationEventType?,
        channel: NotificationChannel?,
        status: NotificationDeliveryStatus?,
        from: Instant?,
        to: Instant?,
    ): Specification<NotificationDeliveryLog> =
        Specification { root, _, cb ->
            val predicates = mutableListOf(cb.equal(root.get<UUID>("tenantId"), tenantId))
            eventType?.let { predicates += cb.equal(root.get<NotificationEventType>("eventType"), it) }
            channel?.let { predicates += cb.equal(root.get<NotificationChannel>("channel"), it) }
            status?.let { predicates += cb.equal(root.get<NotificationDeliveryStatus>("status"), it) }
            from?.let { predicates += cb.greaterThanOrEqualTo(root.get<Instant>("createdAt"), it) }
            to?.let { predicates += cb.lessThanOrEqualTo(root.get<Instant>("createdAt"), it) }
            cb.and(*predicates.toTypedArray())
        }

    private companion object {
        const val MAX_ERROR_LENGTH = 1000
        val SENSITIVE_METADATA_KEYS = listOf("token", "secret", "password", "credential", "apiKey")
    }
}
