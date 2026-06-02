package com.hadivo.attendance.modules.leave.balance

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class UpdateLeavePolicyRequest(
    val name: String? = null,
    val annualLeaveQuotaDays: BigDecimal? = null,
    val sickLeaveRequiresBalance: Boolean? = null,
    val permissionRequiresBalance: Boolean? = null,
    val businessTripRequiresBalance: Boolean? = null,
    val active: Boolean? = null,
)

data class LeavePolicyView(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val annualLeaveQuotaDays: BigDecimal,
    val sickLeaveRequiresBalance: Boolean,
    val permissionRequiresBalance: Boolean,
    val businessTripRequiresBalance: Boolean,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
