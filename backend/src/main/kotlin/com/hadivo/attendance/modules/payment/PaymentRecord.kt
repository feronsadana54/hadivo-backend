package com.hadivo.attendance.modules.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment_records")
class PaymentRecord(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "package_id")
    var packageId: UUID? = null,

    @Column(name = "subscription_id")
    var subscriptionId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var provider: PaymentProvider,

    @Column(name = "provider_order_id", nullable = false, unique = true, length = 50)
    var providerOrderId: String,

    @Column(name = "provider_transaction_id", length = 120)
    var providerTransactionId: String? = null,

    @Column(name = "payment_url")
    var paymentUrl: String? = null,

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    var grossAmount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String = DEFAULT_CURRENCY,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(name = "expired_at")
    var expiredAt: Instant? = null,

    @Column(name = "raw_webhook_json", columnDefinition = "text")
    var rawWebhookJson: String? = null,

    @Column(name = "created_by")
    var createdBy: UUID? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touchUpdatedAt() {
        updatedAt = Instant.now()
    }

    private companion object {
        const val DEFAULT_CURRENCY = "IDR"
    }
}
