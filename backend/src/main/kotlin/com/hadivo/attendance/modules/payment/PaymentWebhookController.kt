package com.hadivo.attendance.modules.payment

import com.fasterxml.jackson.databind.JsonNode
import com.hadivo.attendance.common.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments/webhooks")
class PaymentWebhookController(
    private val service: PaymentService,
) {

    @PostMapping("/midtrans")
    fun midtrans(@RequestBody payload: JsonNode): ApiResponse<PaymentWebhookResult> =
        ApiResponse.ok(service.processMidtransWebhook(payload))
}
