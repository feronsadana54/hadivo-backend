package com.hadivo.attendance.modules.superadmin

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.response.PageResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.subscription.SubscriptionStatus
import com.hadivo.attendance.modules.tenant.TenantMode
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/super-admin")
class SuperAdminController(
    private val service: SuperAdminService,
    private val guard: SuperAdminGuard,
    private val audit: AuditLogger,
) {

    @GetMapping("/overview")
    fun overview(@CurrentUser principal: AuthPrincipal): ApiResponse<SuperAdminOverviewResponse> {
        guard.requireSuperAdmin(principal)
        audit.log(
            tenantId = null,
            actorUserId = principal.userId,
            action = "SUPER_ADMIN_OVERVIEW_VIEWED",
            resourceType = "SuperAdminConsole",
        )
        return ApiResponse.ok(service.overview())
    }

    @GetMapping("/tenants")
    fun tenants(
        @RequestParam(required = false) type: TenantMode?,
        @RequestParam(required = false) status: TenantStatus?,
        @RequestParam(required = false) subscriptionStatus: SubscriptionStatus?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<PageResponse<SuperAdminTenantListItem>> {
        guard.requireSuperAdmin(principal)
        audit.log(
            tenantId = null,
            actorUserId = principal.userId,
            action = "SUPER_ADMIN_TENANT_LIST_VIEWED",
            resourceType = "SuperAdminConsole",
            metadata = mapOf(
                "type" to type?.name,
                "status" to status?.name,
                "subscriptionStatus" to subscriptionStatus?.name,
                "searchPresent" to !search.isNullOrBlank(),
                "page" to page,
                "size" to size,
            ),
        )
        return ApiResponse.ok(
            service.listTenants(
                type = type,
                status = status,
                subscriptionStatus = subscriptionStatus,
                search = search,
                page = page,
                size = size,
            )
        )
    }

    @GetMapping("/tenants/{tenantId}")
    fun detail(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SuperAdminTenantDetailResponse> {
        guard.requireSuperAdmin(principal)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "SUPER_ADMIN_TENANT_DETAIL_VIEWED",
            resourceType = "Tenant",
            resourceId = tenantId.toString(),
        )
        return ApiResponse.ok(service.detail(tenantId))
    }
}
