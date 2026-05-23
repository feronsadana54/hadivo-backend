package com.hadivo.attendance.modules.notification

import com.hadivo.attendance.config.AppProperties
import com.hadivo.attendance.modules.audit.AuditLogger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

@Component
class NotificationPublisher(
    private val rabbit: RabbitTemplate,
    private val props: AppProperties,
    private val audit: AuditLogger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(request: NotificationRequest) {
        try {
            rabbit.convertAndSend(props.messaging.exchange, props.messaging.notificationRoutingKey, request)
            audit.log(
                tenantId = request.tenantId,
                actorUserId = request.actorUserId,
                action = "NOTIFICATION_PUBLISHED",
                resourceType = "NotificationRequest",
                resourceId = request.eventType.name,
                metadata = mapOf("eventType" to request.eventType.name),
            )
        } catch (ex: Exception) {
            log.warn("Failed to publish notification {} to RabbitMQ: {}", request.eventType, ex.message)
        }
    }
}
