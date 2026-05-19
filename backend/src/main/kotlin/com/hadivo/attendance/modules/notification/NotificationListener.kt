package com.hadivo.attendance.modules.notification

import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.parentlink.ParentLinkService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class NotificationListener(
    private val notifications: NotificationRepository,
    private val memberships: MembershipRepository,
    private val parentLinks: ParentLinkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    @RabbitListener(queues = ["\${hadivo.messaging.notification-queue}"])
    fun handle(message: NotificationMessage) {
        log.debug("Received notification {} for user={} tenant={}", message.type, message.userId, message.tenantId)
        notifications.save(buildNotification(message, message.userId))

        val membership = memberships.findByTenantIdAndUserId(message.tenantId, message.userId)
        if (membership?.role == Role.STUDENT) {
            parentLinks.activeParentsOf(message.tenantId, message.userId).forEach { parentId ->
                notifications.save(buildNotification(message, parentId))
            }
        }
    }

    private fun buildNotification(message: NotificationMessage, recipient: java.util.UUID): Notification =
        Notification(
            tenantId = message.tenantId,
            recipientUserId = recipient,
            type = message.type,
            payload = mapOf(
                "actorUserId" to message.userId.toString(),
                "occurredAt" to message.occurredAt.toString(),
                "data" to message.data,
            ),
        )
}
