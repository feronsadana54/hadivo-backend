package com.hadivo.attendance.modules.superadmin

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.response.PageResponse
import com.hadivo.attendance.modules.attendance.AttendanceAttempt
import com.hadivo.attendance.modules.attendance.AttendanceAttemptRepository
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.subscription.Subscription
import com.hadivo.attendance.modules.subscription.SubscriptionRepository
import com.hadivo.attendance.modules.subscription.SubscriptionStatus
import com.hadivo.attendance.modules.tenant.Tenant
import com.hadivo.attendance.modules.tenant.TenantMode
import com.hadivo.attendance.modules.tenant.TenantRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class SuperAdminService(
    private val tenants: TenantRepository,
    private val memberships: MembershipRepository,
    private val records: AttendanceRecordRepository,
    private val attempts: AttendanceAttemptRepository,
    private val subscriptions: SubscriptionRepository,
    private val users: UserRepository,
) {

    @Transactional(readOnly = true)
    fun overview(): SuperAdminOverviewResponse {
        val tenantRows = tenants.findAll()
        val subscriptionCounts = SubscriptionStatus.entries.associateWith { subscriptions.countByStatus(it) }

        return SuperAdminOverviewResponse(
            totalTenants = tenants.count(),
            activeTenants = tenants.countByActive(true),
            companyTenants = tenants.countByMode(TenantMode.COMPANY),
            schoolTenants = tenants.countByMode(TenantMode.SCHOOL),
            totalMembers = memberships.count(),
            attendanceToday = tenantRows.sumOf { tenant ->
                records.countByTenantIdAndDate(tenant.requiredId(), dayWindow(tenant).date)
            },
            failedAttemptsToday = tenantRows.sumOf { tenant ->
                val window = dayWindow(tenant)
                attempts.countByTenantIdAndCreatedAtBetween(tenant.requiredId(), window.from, window.to)
            },
            activeSubscriptions = subscriptionCounts[SubscriptionStatus.ACTIVE] ?: 0,
            expiredSubscriptions = subscriptionCounts[SubscriptionStatus.EXPIRED] ?: 0,
            subscriptionStatusCounts = subscriptionCounts,
            generatedAt = Instant.now(),
        )
    }

    @Transactional(readOnly = true)
    fun listTenants(
        type: TenantMode?,
        status: TenantStatus?,
        subscriptionStatus: SubscriptionStatus?,
        search: String?,
        page: Int,
        size: Int,
    ): PageResponse<SuperAdminTenantListItem> {
        val normalizedSearch = search?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val filtered = tenants.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .asSequence()
            .filter { tenant -> type == null || tenant.mode == type }
            .filter { tenant -> status == null || tenant.status() == status }
            .filter { tenant ->
                normalizedSearch == null ||
                    tenant.name.lowercase().contains(normalizedSearch) ||
                    tenant.slug.lowercase().contains(normalizedSearch)
            }
            .map { tenant -> tenant.toListItem() }
            .filter { tenant -> subscriptionStatus == null || tenant.subscriptionStatus == subscriptionStatus }
            .toList()

        val offset = safePage * safeSize
        return PageResponse(
            items = filtered.drop(offset).take(safeSize),
            page = safePage,
            size = safeSize,
            totalItems = filtered.size.toLong(),
            totalPages = if (filtered.isEmpty()) 0 else (filtered.size + safeSize - 1) / safeSize,
        )
    }

    @Transactional(readOnly = true)
    fun detail(tenantId: UUID): SuperAdminTenantDetailResponse {
        val tenant = tenants.findById(tenantId).orElseThrow { DomainException.notFound("Tenant", tenantId) }
        val window = dayWindow(tenant)
        val recentAttempts = attempts.findTop10ByTenantIdOrderByCreatedAtDesc(tenantId)
        val usersById = users.findAllById(recentAttempts.map { it.userId }.distinct())
            .associateBy { it.id ?: error("User id null") }

        return SuperAdminTenantDetailResponse(
            tenantId = tenantId,
            tenantName = tenant.name,
            tenantSlug = tenant.slug,
            tenantType = tenant.mode,
            timezone = tenant.timezone,
            active = tenant.active,
            status = tenant.status(),
            memberCount = memberships.countByTenantId(tenantId),
            activeMemberCount = memberships.countByTenantIdAndActive(tenantId, true),
            attendanceToday = records.countByTenantIdAndDate(tenantId, window.date),
            failedAttemptsToday = attempts.countByTenantIdAndCreatedAtBetween(tenantId, window.from, window.to),
            subscriptionCurrent = subscriptions.findFirstByTenantIdOrderByStartedAtDesc(tenantId)?.toSummary(),
            recentFailedAttempts = recentAttempts.map { attempt ->
                val user = usersById[attempt.userId]
                attempt.toFailedAttempt(fullName = user?.fullName, email = user?.email)
            },
            createdAt = tenant.createdAt,
        )
    }

    private fun Tenant.toListItem(): SuperAdminTenantListItem {
        val tenantId = requiredId()
        val window = dayWindow(this)
        val subscription = subscriptions.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
        return SuperAdminTenantListItem(
            tenantId = tenantId,
            tenantName = name,
            tenantType = mode,
            active = active,
            status = status(),
            memberCount = memberships.countByTenantId(tenantId),
            attendanceToday = records.countByTenantIdAndDate(tenantId, window.date),
            failedAttemptsToday = attempts.countByTenantIdAndCreatedAtBetween(tenantId, window.from, window.to),
            subscriptionPlan = subscription?.plan,
            subscriptionStatus = subscription?.status,
            createdAt = createdAt,
        )
    }

    private fun Subscription.toSummary(): SuperAdminSubscriptionSummary =
        SuperAdminSubscriptionSummary(
            plan = plan,
            status = status,
            maxMembers = maxMembers,
            startedAt = startedAt,
            expiresAt = expiresAt,
        )

    private fun AttendanceAttempt.toFailedAttempt(fullName: String?, email: String?): SuperAdminFailedAttempt =
        SuperAdminFailedAttempt(
            attemptId = id ?: error("Attempt id null"),
            userId = userId,
            fullName = fullName,
            email = email,
            type = type,
            reason = reason,
            createdAt = createdAt,
        )

    private fun Tenant.status(): TenantStatus =
        if (active) TenantStatus.ACTIVE else TenantStatus.INACTIVE

    private fun Tenant.requiredId(): UUID = id ?: error("Tenant id null")

    private fun dayWindow(tenant: Tenant): TenantDayWindow {
        val zone = tenant.timezone.toZoneId()
        val date = LocalDate.now(zone)
        return TenantDayWindow(
            date = date,
            from = date.atStartOfDay(zone).toInstant(),
            to = date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }

    private fun String.toZoneId(): ZoneId =
        runCatching { ZoneId.of(this) }.getOrDefault(DEFAULT_ZONE)

    private data class TenantDayWindow(
        val date: LocalDate,
        val from: Instant,
        val to: Instant,
    )

    private companion object {
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")
    }
}
