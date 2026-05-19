package com.hadivo.attendance.modules.subscription

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findFirstByTenantIdAndStatusOrderByStartedAtDesc(
        tenantId: UUID,
        status: SubscriptionStatus,
    ): Subscription?
}
