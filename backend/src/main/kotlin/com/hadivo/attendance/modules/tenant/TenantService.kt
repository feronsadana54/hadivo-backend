package com.hadivo.attendance.modules.tenant

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.membership.Membership
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.settings.TenantAttendanceSettings
import com.hadivo.attendance.modules.settings.TenantAttendanceSettingsRepository
import com.hadivo.attendance.modules.subscription.CreateSubscriptionRequest
import com.hadivo.attendance.modules.subscription.SubscriptionPlan
import com.hadivo.attendance.modules.subscription.SubscriptionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TenantService(
    private val tenants: TenantRepository,
    private val memberships: MembershipRepository,
    private val settings: TenantAttendanceSettingsRepository,
    private val subscriptions: SubscriptionService,
) {

    @Transactional
    fun create(request: CreateTenantRequest, creatorUserId: UUID): Tenant {
        if (tenants.existsBySlug(request.slug)) {
            throw DomainException.conflict("Slug tenant sudah dipakai")
        }
        val tenant = tenants.save(
            Tenant(
                name = request.name,
                slug = request.slug,
                mode = request.mode,
                timezone = request.timezone,
            )
        )
        val tenantId = tenant.id ?: error("Tenant id null")

        memberships.save(
            Membership(tenantId = tenantId, userId = creatorUserId, role = Role.TENANT_ADMIN)
        )
        settings.save(TenantAttendanceSettings(tenantId = tenantId))
        subscriptions.create(tenantId, CreateSubscriptionRequest(plan = SubscriptionPlan.FREE))

        return tenant
    }

    fun get(tenantId: UUID): Tenant =
        tenants.findById(tenantId).orElseThrow { DomainException.notFound("Tenant", tenantId) }

    @Transactional
    fun update(tenantId: UUID, request: UpdateTenantRequest): Tenant {
        val tenant = get(tenantId)
        request.name?.let { tenant.name = it }
        request.timezone?.let { tenant.timezone = it }
        request.active?.let { tenant.active = it }
        return tenants.save(tenant)
    }
}

fun Tenant.toView(): TenantView = TenantView(
    id = id ?: error("Tenant belum tersimpan"),
    name = name,
    slug = slug,
    mode = mode,
    timezone = timezone,
    active = active,
)
