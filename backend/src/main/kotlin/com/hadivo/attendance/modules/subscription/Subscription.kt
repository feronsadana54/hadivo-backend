package com.hadivo.attendance.modules.subscription

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscriptions")
class Subscription(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var plan: SubscriptionPlan,

    @Column(name = "max_members", nullable = false)
    var maxMembers: Int,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
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
}
