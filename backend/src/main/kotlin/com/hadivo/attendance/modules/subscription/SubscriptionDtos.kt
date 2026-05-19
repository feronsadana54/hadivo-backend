package com.hadivo.attendance.modules.subscription

import java.time.Instant
import java.util.UUID

data class CreateSubscriptionRequest(
    val plan: SubscriptionPlan,
    val expiresAt: Instant? = null,
)

data class SubscriptionView(
    val id: UUID,
    val tenantId: UUID,
    val plan: SubscriptionPlan,
    val maxMembers: Int,
    val startedAt: Instant,
    val expiresAt: Instant?,
    val status: SubscriptionStatus,
)
