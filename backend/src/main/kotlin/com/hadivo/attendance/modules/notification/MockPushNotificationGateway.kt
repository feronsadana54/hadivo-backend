package com.hadivo.attendance.modules.notification

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnExpression("'\${hadivo.notification.push.provider:mock}' != 'fcm' || '\${hadivo.notification.push.fcm.enabled:false}' != 'true' || '\${hadivo.notification.push.fcm.project-id:}' == '' || '\${hadivo.notification.push.fcm.service-account-path:}' == ''")
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
        if (recipient.userId == null || destination.isNullOrBlank()) {
            return NotificationGatewayResult(
                status = NotificationDeliveryStatus.SKIPPED,
                provider = PROVIDER,
                errorMessage = "Recipient push destination is not available",
            )
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
