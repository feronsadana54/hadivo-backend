package com.hadivo.attendance.modules.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.config.AppProperties
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.subscription.Subscription
import com.hadivo.attendance.modules.subscription.SubscriptionRepository
import com.hadivo.attendance.modules.subscription.SubscriptionStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PaymentService(
    private val packages: SubscriptionPackageRepository,
    private val payments: PaymentRecordRepository,
    private val subscriptions: SubscriptionRepository,
    private val gateways: List<PaymentGateway>,
    private val props: AppProperties,
    private val audit: AuditLogger,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun listPackages(): List<SubscriptionPackageView> =
        packages.findAllByActiveOrderByGrossAmountAsc().map { it.toView() }

    @Transactional(readOnly = true)
    fun listPayments(tenantId: UUID): List<PaymentView> =
        payments.findAllByTenantIdOrderByCreatedAtDesc(tenantId).map { it.toView() }

    @Transactional(readOnly = true)
    fun getPayment(tenantId: UUID, paymentId: UUID): PaymentView =
        (payments.findByIdAndTenantId(paymentId, tenantId) ?: throw DomainException.notFound("PaymentRecord", paymentId))
            .toView()

    @Transactional
    fun createPayment(
        tenantId: UUID,
        actorUserId: UUID,
        request: CreateSubscriptionPaymentRequest,
    ): PaymentView {
        val packageId = request.packageId
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Package wajib dipilih")
        val subscriptionPackage = packages.findById(packageId).orElseThrow {
            DomainException.notFound("SubscriptionPackage", packageId)
        }
        if (!subscriptionPackage.active) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Package subscription tidak aktif")
        }
        if (request.billingPeriod != null && request.billingPeriod != subscriptionPackage.billingPeriod) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Billing period tidak sesuai package")
        }

        val gateway = resolveGateway()
        val expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
        val payment = payments.save(
            PaymentRecord(
                tenantId = tenantId,
                packageId = packageId,
                provider = gateway.provider,
                providerOrderId = generateOrderId(tenantId),
                grossAmount = subscriptionPackage.grossAmount,
                currency = subscriptionPackage.currency,
                status = PaymentStatus.PENDING,
                expiredAt = expiresAt,
                createdBy = actorUserId,
            )
        )

        try {
            val gatewayResponse = gateway.createPayment(
                PaymentRequest(
                    providerOrderId = payment.providerOrderId,
                    grossAmount = payment.grossAmount,
                    currency = payment.currency,
                    packageCode = subscriptionPackage.code,
                    packageName = subscriptionPackage.name,
                    customerName = request.customerName,
                    customerEmail = request.customerEmail,
                    expiredAt = expiresAt,
                )
            )
            payment.providerTransactionId = gatewayResponse.providerTransactionId
            payment.paymentUrl = gatewayResponse.paymentUrl
            payment.expiredAt = gatewayResponse.expiredAt
            payments.save(payment)
        } catch (ex: RuntimeException) {
            payment.status = PaymentStatus.FAILED
            payments.save(payment)
            auditPaymentStatusUpdated(payment, PaymentStatus.PENDING, PaymentStatus.FAILED, "gateway_error")
            throw DomainException(ErrorCode.UNPROCESSABLE, "Payment provider belum tersedia. Coba lagi beberapa saat.")
        }

        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "PAYMENT_CREATED",
            resourceType = "PaymentRecord",
            resourceId = payment.id?.toString(),
            metadata = mapOf(
                "provider" to payment.provider.name,
                "providerOrderId" to payment.providerOrderId,
                "status" to payment.status.name,
                "packageCode" to subscriptionPackage.code,
                "billingPeriod" to subscriptionPackage.billingPeriod.name,
            ),
        )
        return payment.toView()
    }

    @Transactional
    fun processMidtransWebhook(payload: JsonNode): PaymentWebhookResult {
        val webhook = payload.toMidtransPayload()
        if (webhook.orderId.isBlank()) {
            auditWebhookIgnored(null, null, "missing_order_id")
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Webhook payment tidak valid")
        }

        val payment = payments.findByProviderOrderIdForUpdate(webhook.orderId)
            ?: run {
                auditWebhookIgnored(null, webhook.orderId, "unknown_order_id")
                throw DomainException(ErrorCode.VALIDATION_FAILED, "Webhook payment tidak valid")
            }

        audit.log(
            tenantId = payment.tenantId,
            actorUserId = null,
            action = "PAYMENT_WEBHOOK_RECEIVED",
            resourceType = "PaymentRecord",
            resourceId = payment.id?.toString(),
            metadata = mapOf(
                "provider" to PaymentProvider.MIDTRANS.name,
                "providerOrderId" to payment.providerOrderId,
                "transactionStatus" to webhook.transactionStatus,
            ),
        )

        if (!isValidMidtransSignature(webhook)) {
            auditWebhookIgnored(payment, webhook.orderId, "invalid_signature")
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Webhook payment tidak valid")
        }
        if (!amountMatches(payment, webhook.grossAmount)) {
            auditWebhookIgnored(payment, webhook.orderId, "amount_mismatch")
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Webhook payment tidak valid")
        }

        val sanitizedRaw = sanitizeWebhookPayload(payload)
        val newStatus = mapMidtransStatus(webhook)
        val previousStatus = payment.status
        payment.providerTransactionId = webhook.transactionId ?: payment.providerTransactionId
        payment.rawWebhookJson = sanitizedRaw

        if (shouldIgnoreWebhook(previousStatus, newStatus)) {
            payments.save(payment)
            auditWebhookIgnored(payment, webhook.orderId, "status_${previousStatus.name.lowercase()}_blocks_${newStatus.name.lowercase()}")
            return PaymentWebhookResult(payment.providerOrderId, payment.status, ignored = true)
        }
        if (previousStatus == newStatus) {
            payments.save(payment)
            auditWebhookIgnored(payment, webhook.orderId, "duplicate_${newStatus.name.lowercase()}")
            return PaymentWebhookResult(payment.providerOrderId, payment.status, ignored = true)
        }

        payment.status = newStatus
        if (newStatus == PaymentStatus.PAID) {
            payment.paidAt = payment.paidAt ?: Instant.now()
        }
        payments.save(payment)
        auditPaymentStatusUpdated(payment, previousStatus, newStatus, "midtrans_webhook")

        if (newStatus == PaymentStatus.PAID && payment.subscriptionId == null) {
            val subscription = activateSubscription(payment)
            payment.subscriptionId = subscription.id
            payments.save(payment)
            audit.log(
                tenantId = payment.tenantId,
                actorUserId = null,
                action = "SUBSCRIPTION_ACTIVATED",
                resourceType = "Subscription",
                resourceId = subscription.id?.toString(),
                metadata = mapOf(
                    "paymentId" to payment.id?.toString(),
                    "providerOrderId" to payment.providerOrderId,
                    "plan" to subscription.plan.name,
                    "expiresAt" to subscription.expiresAt?.toString(),
                ),
            )
        }

        return PaymentWebhookResult(payment.providerOrderId, payment.status)
    }

    private fun resolveGateway(): PaymentGateway {
        val requested = PaymentProvider.from(props.payment.provider)
        val midtrans = props.payment.midtrans
        if (
            requested == PaymentProvider.MIDTRANS &&
            midtrans.enabled &&
            midtrans.serverKey.isNotBlank()
        ) {
            gateways.firstOrNull { it.provider == PaymentProvider.MIDTRANS }?.let { return it }
        }
        return gateways.first { it.provider == PaymentProvider.MOCK }
    }

    private fun generateOrderId(tenantId: UUID): String {
        val date = ORDER_DATE_FORMATTER.format(Instant.now())
        val tenantCode = tenantId.toString().substringBefore("-").uppercase()
        val random = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "HADIVO-$date-$tenantCode-$random".take(MAX_ORDER_ID_LENGTH)
    }

    private fun isValidMidtransSignature(payload: PaymentWebhookPayload): Boolean {
        val serverKey = props.payment.midtrans.serverKey
        if (serverKey.isBlank()) return false
        val statusCode = payload.statusCode ?: return false
        val grossAmount = payload.grossAmount ?: return false
        val signature = payload.signatureKey ?: return false
        val expected = sha512(payload.orderId + statusCode + grossAmount + serverKey)
        return expected.equals(signature, ignoreCase = true)
    }

    private fun amountMatches(payment: PaymentRecord, grossAmount: String?): Boolean {
        val payloadAmount = grossAmount?.toBigDecimalOrNull() ?: return false
        return payment.grossAmount.compareTo(payloadAmount) == 0
    }

    private fun mapMidtransStatus(payload: PaymentWebhookPayload): PaymentStatus =
        when (payload.transactionStatus?.lowercase()) {
            "settlement" -> PaymentStatus.PAID
            "capture" -> if (payload.fraudStatus.isNullOrBlank() || payload.fraudStatus.equals("accept", true)) {
                PaymentStatus.PAID
            } else {
                PaymentStatus.FAILED
            }
            "pending" -> PaymentStatus.PENDING
            "expire" -> PaymentStatus.EXPIRED
            "cancel" -> PaymentStatus.CANCELLED
            "deny", "failure" -> PaymentStatus.FAILED
            else -> PaymentStatus.FAILED
        }

    private fun shouldIgnoreWebhook(current: PaymentStatus, incoming: PaymentStatus): Boolean {
        if (current == PaymentStatus.PAID && incoming != PaymentStatus.PAID) return true
        if (current in terminalStatuses && current != PaymentStatus.PAID && incoming != current) return true
        return false
    }

    private fun activateSubscription(payment: PaymentRecord): Subscription {
        payment.subscriptionId?.let { subscriptionId ->
            return subscriptions.findById(subscriptionId).orElseThrow {
                DomainException.notFound("Subscription", subscriptionId)
            }
        }
        val packageId = payment.packageId ?: throw DomainException(ErrorCode.UNPROCESSABLE, "Payment tidak memiliki package")
        val subscriptionPackage = packages.findById(packageId).orElseThrow {
            DomainException.notFound("SubscriptionPackage", packageId)
        }
        val now = payment.paidAt ?: Instant.now()
        val existing = subscriptions.findFirstByTenantIdAndStatusOrderByStartedAtDesc(
            payment.tenantId,
            SubscriptionStatus.ACTIVE,
        )

        if (existing != null && existing.plan == subscriptionPackage.plan) {
            val base = existing.expiresAt?.takeIf { it.isAfter(now) } ?: now
            existing.maxMembers = subscriptionPackage.plan.maxMembers
            existing.expiresAt = addMonths(base, subscriptionPackage.durationMonths)
            existing.status = SubscriptionStatus.ACTIVE
            return subscriptions.save(existing)
        }

        if (existing != null) {
            existing.status = SubscriptionStatus.CANCELLED
            subscriptions.save(existing)
        }

        return subscriptions.save(
            Subscription(
                tenantId = payment.tenantId,
                plan = subscriptionPackage.plan,
                maxMembers = subscriptionPackage.plan.maxMembers,
                startedAt = now,
                expiresAt = addMonths(now, subscriptionPackage.durationMonths),
                status = SubscriptionStatus.ACTIVE,
            )
        )
    }

    private fun addMonths(value: Instant, months: Int): Instant =
        value.atZone(ZoneOffset.UTC).plusMonths(months.toLong()).toInstant()

    private fun JsonNode.toMidtransPayload(): PaymentWebhookPayload =
        PaymentWebhookPayload(
            orderId = text("order_id").orEmpty(),
            transactionStatus = text("transaction_status"),
            statusCode = text("status_code"),
            grossAmount = text("gross_amount"),
            signatureKey = text("signature_key"),
            transactionId = text("transaction_id"),
            fraudStatus = text("fraud_status"),
        )

    private fun JsonNode.text(field: String): String? =
        get(field)?.takeIf { !it.isNull }?.asText()

    private fun sanitizeWebhookPayload(payload: JsonNode): String {
        val copy = payload.deepCopy<JsonNode>()
        sanitizeNode(copy)
        return objectMapper.writeValueAsString(copy)
    }

    private fun sanitizeNode(node: JsonNode) {
        when (node) {
            is ObjectNode -> {
                val fields = node.fieldNames().asSequence().toList()
                fields.forEach { field ->
                    if (sensitiveWebhookFields.any { field.contains(it, ignoreCase = true) }) {
                        node.put(field, "[redacted]")
                    } else {
                        sanitizeNode(node.get(field))
                    }
                }
            }
            is ArrayNode -> node.forEach(::sanitizeNode)
        }
    }

    private fun auditPaymentStatusUpdated(
        payment: PaymentRecord,
        from: PaymentStatus,
        to: PaymentStatus,
        reason: String,
    ) {
        audit.log(
            tenantId = payment.tenantId,
            actorUserId = null,
            action = "PAYMENT_STATUS_UPDATED",
            resourceType = "PaymentRecord",
            resourceId = payment.id?.toString(),
            metadata = mapOf(
                "providerOrderId" to payment.providerOrderId,
                "from" to from.name,
                "to" to to.name,
                "reason" to reason,
            ),
        )
    }

    private fun auditWebhookIgnored(payment: PaymentRecord?, providerOrderId: String?, reason: String) {
        audit.log(
            tenantId = payment?.tenantId,
            actorUserId = null,
            action = "PAYMENT_WEBHOOK_IGNORED",
            resourceType = "PaymentRecord",
            resourceId = payment?.id?.toString(),
            metadata = mapOf(
                "provider" to PaymentProvider.MIDTRANS.name,
                "providerOrderId" to providerOrderId,
                "reason" to reason,
            ),
        )
    }

    private fun sha512(value: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ORDER_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
        const val MAX_ORDER_ID_LENGTH = 50
        val terminalStatuses = setOf(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED)
        val sensitiveWebhookFields = listOf("key", "secret", "token", "authorization", "signature")
    }
}
