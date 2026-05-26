package com.hadivo.attendance.modules.payment

import com.hadivo.attendance.modules.subscription.SubscriptionPlan
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateSubscriptionPaymentRequest(
    @field:NotNull
    val packageId: UUID?,
    val billingPeriod: BillingPeriod? = null,
    val customerName: String? = null,
    @field:Email
    val customerEmail: String? = null,
)

data class SubscriptionPackageView(
    val id: UUID,
    val code: String,
    val name: String,
    val plan: SubscriptionPlan,
    val billingPeriod: BillingPeriod,
    val grossAmount: BigDecimal,
    val currency: String,
    val durationMonths: Int,
)

data class PaymentView(
    val paymentId: UUID,
    val provider: PaymentProvider,
    val providerOrderId: String,
    val status: PaymentStatus,
    val grossAmount: BigDecimal,
    val currency: String,
    val paymentUrl: String?,
    val paidAt: Instant?,
    val expiredAt: Instant?,
    val createdAt: Instant,
)

data class PaymentWebhookResult(
    val providerOrderId: String,
    val status: PaymentStatus,
    val ignored: Boolean = false,
)

data class PaymentRequest(
    val providerOrderId: String,
    val grossAmount: BigDecimal,
    val currency: String,
    val packageCode: String,
    val packageName: String,
    val customerName: String?,
    val customerEmail: String?,
    val expiredAt: Instant?,
)

data class PaymentResponse(
    val provider: PaymentProvider,
    val providerTransactionId: String?,
    val paymentUrl: String?,
    val expiredAt: Instant?,
)

data class PaymentWebhookPayload(
    val orderId: String,
    val transactionStatus: String?,
    val statusCode: String?,
    val grossAmount: String?,
    val signatureKey: String?,
    val transactionId: String?,
    val fraudStatus: String?,
)

fun SubscriptionPackage.toView(): SubscriptionPackageView =
    SubscriptionPackageView(
        id = id ?: error("package id null"),
        code = code,
        name = name,
        plan = plan,
        billingPeriod = billingPeriod,
        grossAmount = grossAmount,
        currency = currency,
        durationMonths = durationMonths,
    )

fun PaymentRecord.toView(): PaymentView =
    PaymentView(
        paymentId = id ?: error("payment id null"),
        provider = provider,
        providerOrderId = providerOrderId,
        status = status,
        grossAmount = grossAmount,
        currency = currency,
        paymentUrl = paymentUrl,
        paidAt = paidAt,
        expiredAt = expiredAt,
        createdAt = createdAt,
    )
