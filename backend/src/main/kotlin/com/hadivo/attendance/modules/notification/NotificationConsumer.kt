package com.hadivo.attendance.modules.notification

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class NotificationConsumer(private val service: NotificationService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = ["\${hadivo.messaging.notification-events-queue}"])
    fun handle(request: NotificationRequest) {
        try {
            service.process(request)
        } catch (ex: Exception) {
            log.warn("Notification processing failed for event={}: {}", request.eventType, ex.message)
        }
    }
}
