package com.hadivo.attendance.modules.leave.balance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "leave_balances")
class LeaveBalance(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(nullable = false)
    var year: Int,

    @Column(name = "annual_quota_days", nullable = false)
    var annualQuotaDays: BigDecimal,

    @Column(name = "used_days", nullable = false)
    var usedDays: BigDecimal = BigDecimal.ZERO,

    @Column(name = "adjusted_days", nullable = false)
    var adjustedDays: BigDecimal = BigDecimal.ZERO,

    @Column(name = "remaining_days", nullable = false)
    var remainingDays: BigDecimal = BigDecimal.ZERO,
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

    fun recomputeRemaining() {
        remainingDays = (annualQuotaDays + adjustedDays - usedDays).setScale(2, java.math.RoundingMode.HALF_UP)
    }
}
