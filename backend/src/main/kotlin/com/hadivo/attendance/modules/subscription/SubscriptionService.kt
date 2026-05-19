package com.hadivo.attendance.modules.subscription

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.membership.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptions: SubscriptionRepository,
    private val memberships: MembershipRepository,
) {

    @Transactional
    fun create(tenantId: UUID, request: CreateSubscriptionRequest): Subscription {
        val existing = subscriptions.findFirstByTenantIdAndStatusOrderByStartedAtDesc(
            tenantId, SubscriptionStatus.ACTIVE,
        )
        if (existing != null) {
            existing.status = SubscriptionStatus.CANCELLED
            subscriptions.save(existing)
        }
        val sub = Subscription(
            tenantId = tenantId,
            plan = request.plan,
            maxMembers = request.plan.maxMembers,
            startedAt = Instant.now(),
            expiresAt = request.expiresAt,
        )
        return subscriptions.save(sub)
    }

    fun currentOrThrow(tenantId: UUID): Subscription =
        subscriptions.findFirstByTenantIdAndStatusOrderByStartedAtDesc(tenantId, SubscriptionStatus.ACTIVE)
            ?: throw DomainException(ErrorCode.UNPROCESSABLE, "Tenant tidak memiliki subscription aktif")

    fun ensureCanAddMember(tenantId: UUID) {
        val active = subscriptions.findFirstByTenantIdAndStatusOrderByStartedAtDesc(
            tenantId, SubscriptionStatus.ACTIVE,
        ) ?: throw DomainException(ErrorCode.UNPROCESSABLE, "Tenant tidak memiliki subscription aktif")
        if (active.plan.isUnlimited()) return
        val current = memberships.countByTenantIdAndActive(tenantId, true)
        if (current >= active.maxMembers) {
            throw DomainException(
                ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED,
                "Batas anggota plan ${active.plan} (${active.maxMembers}) tercapai",
            )
        }
    }
}

fun Subscription.toView(): SubscriptionView = SubscriptionView(
    id = id ?: error("id null"),
    tenantId = tenantId,
    plan = plan,
    maxMembers = maxMembers,
    startedAt = startedAt,
    expiresAt = expiresAt,
    status = status,
)
