package com.hadivo.attendance.modules.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MockEmailNotificationGateway : NotificationGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override val channel: NotificationChannel = NotificationChannel.EMAIL

    override fun send(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
        destination: String?,
    ): NotificationGatewayResult {
        if (request.metadata["simulateEmailFailure"] == true) {
            throw IllegalStateException("Mock email gateway failure")
        }
        if (destination.isNullOrBlank()) {
            return NotificationGatewayResult(
                status = NotificationDeliveryStatus.SKIPPED,
                provider = PROVIDER,
                errorMessage = "Recipient email is not available",
            )
        }
        log.info("Mock email notification event={} destination={}", request.eventType, destination)
        return NotificationGatewayResult(
            status = NotificationDeliveryStatus.SENT,
            provider = PROVIDER,
            providerMessageId = "mock-email-${UUID.randomUUID()}",
        )
    }

    private companion object {
        const val PROVIDER = "mock-email"
    }
}
