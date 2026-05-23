package com.hadivo.attendance.modules.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MockPushNotificationGateway : NotificationGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override val channel: NotificationChannel = NotificationChannel.PUSH

    override fun send(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
        destination: String?,
    ): NotificationGatewayResult {
        if (request.metadata["simulatePushFailure"] == true) {
            throw IllegalStateException("Mock push gateway failure")
        }
        log.info("Mock push notification event={} recipient={}", request.eventType, recipient.userId)
        return NotificationGatewayResult(
            status = NotificationDeliveryStatus.SENT,
            provider = PROVIDER,
            providerMessageId = "mock-push-${UUID.randomUUID()}",
        )
    }

    private companion object {
        const val PROVIDER = "mock-push"
    }
}
