package com.hadivo.attendance.modules.payment

interface PaymentGateway {
    val provider: PaymentProvider
    fun createPayment(request: PaymentRequest): PaymentResponse
}
