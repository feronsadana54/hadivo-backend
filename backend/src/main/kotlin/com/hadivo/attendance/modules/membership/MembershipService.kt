package com.hadivo.attendance.modules.membership

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.subscription.SubscriptionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MembershipService(
    private val memberships: MembershipRepository,
    private val users: UserRepository,
    private val subscriptions: SubscriptionService,
) {

    @Transactional
    fun add(tenantId: UUID, request: CreateMembershipRequest): Membership {
        if (!users.existsById(request.userId)) {
            throw DomainException.notFound("User", request.userId)
        }
        memberships.findByTenantIdAndUserId(tenantId, request.userId)?.let {
            throw DomainException.conflict("User sudah menjadi anggota tenant")
        }
        subscriptions.ensureCanAddMember(tenantId)
        return memberships.save(
            Membership(tenantId = tenantId, userId = request.userId, role = request.role)
        )
    }

    fun list(tenantId: UUID): List<Membership> = memberships.findAllByTenantId(tenantId)

    fun listResponses(tenantId: UUID): List<MembershipResponse> = memberships.findResponsesByTenantId(tenantId)

    @Transactional
    fun remove(tenantId: UUID, membershipId: UUID) {
        val membership = memberships.findById(membershipId)
            .orElseThrow { DomainException.notFound("Membership", membershipId) }
        if (membership.tenantId != tenantId) {
            throw DomainException.notFound("Membership", membershipId)
        }
        memberships.delete(membership)
    }
}

fun Membership.toView(): MembershipView = MembershipView(
    id = id ?: error("Membership belum tersimpan"),
    tenantId = tenantId,
    userId = userId,
    role = role,
    active = active,
)
