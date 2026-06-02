package com.hadivo.attendance.modules.leave.balance

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AdjustLeaveBalanceRequest(
    val year: Int?,
    val days: BigDecimal?,
    val note: String?,
)

data class LeaveBalanceView(
    val id: UUID,
    val tenantId: UUID,
    val userId: UUID,
    val userFullName: String?,
    val userEmail: String?,
    val year: Int,
    val annualQuotaDays: BigDecimal,
    val usedDays: BigDecimal,
    val adjustedDays: BigDecimal,
    val remainingDays: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant,
)
