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
@Table(name = "leave_policies")
class LeavePolicy(
    @Column(name = "tenant_id", nullable = false, unique = true)
    var tenantId: UUID,

    @Column(nullable = false, length = 80)
    var name: String = "Default",

    @Column(name = "annual_leave_quota_days", nullable = false)
    var annualLeaveQuotaDays: BigDecimal = DEFAULT_QUOTA,

    @Column(name = "sick_leave_requires_balance", nullable = false)
    var sickLeaveRequiresBalance: Boolean = false,

    @Column(name = "permission_requires_balance", nullable = false)
    var permissionRequiresBalance: Boolean = false,

    @Column(name = "business_trip_requires_balance", nullable = false)
    var businessTripRequiresBalance: Boolean = false,

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

    companion object {
        val DEFAULT_QUOTA: BigDecimal = BigDecimal("12.00")
        val MAX_QUOTA: BigDecimal = BigDecimal("365.00")
    }
}
