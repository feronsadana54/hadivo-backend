package com.hadivo.attendance.modules.leave.balance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "leave_balance_ledgers")
class LeaveBalanceLedger(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "leave_request_id")
    var leaveRequestId: UUID? = null,

    @Column(nullable = false)
    var year: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    var changeType: LeaveBalanceChangeType,

    @Column(name = "days_changed", nullable = false)
    var daysChanged: BigDecimal,

    @Column(name = "balance_before", nullable = false)
    var balanceBefore: BigDecimal,

    @Column(name = "balance_after", nullable = false)
    var balanceAfter: BigDecimal,

    @Column
    var note: String? = null,

    @Column(name = "created_by")
    var createdBy: UUID? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
