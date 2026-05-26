package com.hadivo.attendance.modules.payment

import org.springframework.stereotype.Component

@Component
class MockPaymentGateway : PaymentGateway {
    override val provider: PaymentProvider = PaymentProvider.MOCK

    override fun createPayment(request: PaymentRequest): PaymentResponse =
        PaymentResponse(
            provider = provider,
            providerTransactionId = "mock-${request.providerOrderId}",
            paymentUrl = "http://localhost:8080/mock-payments/${request.providerOrderId}",
            expiredAt = request.expiredAt,
        )
}
