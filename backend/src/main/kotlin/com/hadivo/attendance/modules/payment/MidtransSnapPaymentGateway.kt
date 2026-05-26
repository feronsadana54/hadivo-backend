package com.hadivo.attendance.modules.payment

import com.fasterxml.jackson.databind.JsonNode
import com.hadivo.attendance.config.AppProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.Base64

@Component
@ConditionalOnExpression("'\${hadivo.payment.provider:mock}' == 'midtrans' && '\${hadivo.payment.midtrans.enabled:false}' == 'true' && '\${hadivo.payment.midtrans.server-key:}' != ''")
class MidtransSnapPaymentGateway(
    private val props: AppProperties,
    restClientBuilder: RestClient.Builder,
) : PaymentGateway {

    private val client = restClientBuilder
        .baseUrl(props.payment.midtrans.snapBaseUrl.trimEnd('/'))
        .defaultHeader("Accept", "application/json")
        .defaultHeader("Content-Type", "application/json")
        .defaultHeader("User-Agent", "Hadivo Payment Gateway")
        .build()

    override val provider: PaymentProvider = PaymentProvider.MIDTRANS

    override fun createPayment(request: PaymentRequest): PaymentResponse {
        val body = mapOf(
            "transaction_details" to mapOf(
                "order_id" to request.providerOrderId,
                "gross_amount" to request.grossAmount.setScale(0, RoundingMode.HALF_UP).toLong(),
            ),
            "customer_details" to mapOf(
                "first_name" to request.customerName.orEmpty().ifBlank { "Hadivo Customer" },
                "email" to request.customerEmail.orEmpty(),
            ),
            "item_details" to listOf(
                mapOf(
                    "id" to request.packageCode.take(MAX_ITEM_ID_LENGTH),
                    "price" to request.grossAmount.setScale(0, RoundingMode.HALF_UP).toLong(),
                    "quantity" to 1,
                    "name" to request.packageName.take(MAX_ITEM_NAME_LENGTH),
                )
            ),
        )

        return try {
            val response = client.post()
                .uri("/snap/v1/transactions")
                .header("Authorization", "Basic ${authorizationToken()}")
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)

            PaymentResponse(
                provider = provider,
                providerTransactionId = response?.get("token")?.asText(),
                paymentUrl = response?.get("redirect_url")?.asText(),
                expiredAt = request.expiredAt,
            )
        } catch (ex: RestClientException) {
            throw IllegalStateException(sanitizeError(ex), ex)
        }
    }

    private fun authorizationToken(): String {
        val value = "${props.payment.midtrans.serverKey}:"
        return Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sanitizeError(ex: Exception): String {
        val midtrans = props.payment.midtrans
        return listOf(midtrans.serverKey, midtrans.clientKey)
            .filter { it.isNotBlank() }
            .fold(ex.message ?: "Midtrans Snap payment failed") { message, secret ->
                message.replace(secret, "[redacted]")
            }
            .take(MAX_ERROR_LENGTH)
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 1000
        const val MAX_ITEM_ID_LENGTH = 50
        const val MAX_ITEM_NAME_LENGTH = 50
    }
}
