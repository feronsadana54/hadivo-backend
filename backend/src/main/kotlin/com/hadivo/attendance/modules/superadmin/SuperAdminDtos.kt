package com.hadivo.attendance.modules.superadmin

import com.hadivo.attendance.modules.attendance.AttemptReason
import com.hadivo.attendance.modules.attendance.AttendanceType
import com.hadivo.attendance.modules.subscription.SubscriptionPlan
import com.hadivo.attendance.modules.subscription.SubscriptionStatus
import com.hadivo.attendance.modules.tenant.TenantMode
import java.time.Instant
import java.util.UUID

enum class TenantStatus {
    ACTIVE,
    INACTIVE,
}

data class SuperAdminOverviewResponse(
    val totalTenants: Long,
    val activeTenants: Long,
    val companyTenants: Long,
    val schoolTenants: Long,
    val totalMembers: Long,
    val attendanceToday: Long,
    val failedAttemptsToday: Long,
    val activeSubscriptions: Long,
    val expiredSubscriptions: Long,
    val subscriptionStatusCounts: Map<SubscriptionStatus, Long>,
    val generatedAt: Instant,
)

data class SuperAdminTenantListItem(
    val tenantId: UUID,
    val tenantName: String,
    val tenantType: TenantMode,
    val active: Boolean,
    val status: TenantStatus,
    val memberCount: Long,
    val attendanceToday: Long,
    val failedAttemptsToday: Long,
    val subscriptionPlan: SubscriptionPlan?,
    val subscriptionStatus: SubscriptionStatus?,
    val createdAt: Instant,
)

data class SuperAdminTenantDetailResponse(
    val tenantId: UUID,
    val tenantName: String,
    val tenantSlug: String,
    val tenantType: TenantMode,
    val timezone: String,
    val active: Boolean,
    val status: TenantStatus,
    val memberCount: Long,
    val activeMemberCount: Long,
    val attendanceToday: Long,
    val failedAttemptsToday: Long,
    val subscriptionCurrent: SuperAdminSubscriptionSummary?,
    val recentFailedAttempts: List<SuperAdminFailedAttempt>,
    val createdAt: Instant,
)

data class SuperAdminSubscriptionSummary(
    val plan: SubscriptionPlan,
    val status: SubscriptionStatus,
    val maxMembers: Int,
    val startedAt: Instant,
    val expiresAt: Instant?,
)

data class SuperAdminFailedAttempt(
    val attemptId: UUID,
    val userId: UUID,
    val fullName: String?,
    val email: String?,
    val type: AttendanceType,
    val reason: AttemptReason,
    val createdAt: Instant,
)
