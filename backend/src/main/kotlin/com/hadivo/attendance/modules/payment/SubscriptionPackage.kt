package com.hadivo.attendance.modules.payment

import com.hadivo.attendance.modules.subscription.SubscriptionPlan
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
@Table(name = "subscription_packages")
class SubscriptionPackage(
    @Column(nullable = false, unique = true, length = 60)
    var code: String,

    @Column(nullable = false, length = 120)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var plan: SubscriptionPlan,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 20)
    var billingPeriod: BillingPeriod,

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    var grossAmount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String = DEFAULT_CURRENCY,

    @Column(name = "duration_months", nullable = false)
    var durationMonths: Int,

    @Column(nullable = false)
    var active: Boolean = true,
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
