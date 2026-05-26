package com.hadivo.attendance.modules.notification

import com.fasterxml.jackson.databind.JsonNode
import com.hadivo.attendance.config.AppProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
@ConditionalOnExpression("'\${hadivo.notification.email.provider:mock}' == 'resend' && '\${hadivo.notification.email.resend.api-key:}' != ''")
class ResendEmailNotificationGateway(
    private val props: AppProperties,
    restClientBuilder: RestClient.Builder,
) : NotificationGateway {

    private val client = restClientBuilder
        .baseUrl("https://api.resend.com")
        .defaultHeader("User-Agent", "Hadivo Notification Gateway")
        .build()

    override val channel: NotificationChannel = NotificationChannel.EMAIL

    override fun send(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
        destination: String?,
    ): NotificationGatewayResult {
        val email = destination?.trim()
        if (email.isNullOrBlank()) {
            return NotificationGatewayResult(
                status = NotificationDeliveryStatus.SKIPPED,
                provider = PROVIDER,
                errorMessage = "Recipient email is not available",
            )
        }

        return try {
            val body = mapOf(
                "from" to props.notification.email.resend.from,
                "to" to listOf(email),
                "subject" to template.title,
                "text" to template.body,
            )
            val response = client.post()
                .uri("/emails")
                .header("Authorization", "Bearer ${props.notification.email.resend.apiKey}")
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)

            NotificationGatewayResult(
                status = NotificationDeliveryStatus.SENT,
                provider = PROVIDER,
                providerMessageId = response?.get("id")?.asText(),
            )
        } catch (ex: RestClientException) {
            NotificationGatewayResult(
                status = NotificationDeliveryStatus.FAILED,
                provider = PROVIDER,
                errorMessage = sanitizeError(ex),
            )
        }
    }

    private fun sanitizeError(ex: Exception): String {
        val apiKey = props.notification.email.resend.apiKey
        return (ex.message ?: "Resend email delivery failed")
            .replace(apiKey, "[redacted]")
            .take(MAX_ERROR_LENGTH)
    }

    private companion object {
        const val PROVIDER = "resend"
        const val MAX_ERROR_LENGTH = 1000
    }
}
