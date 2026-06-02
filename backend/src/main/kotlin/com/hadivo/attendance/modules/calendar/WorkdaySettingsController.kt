package com.hadivo.attendance.modules.calendar

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/workday-settings")
class WorkdaySettingsController(
    private val service: WorkdaySettingsService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @GetMapping
    fun get(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<WorkdaySettingsView> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.getOrCreateDefault(tenantId).toView())
    }

    @PutMapping
    fun update(
        @PathVariable tenantId: UUID,
        @RequestBody request: UpdateWorkdaySettingsRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<WorkdaySettingsView> {
        guard.requireAdmin(principal, tenantId)
        val (settings, changedFields) = service.update(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "WORKDAY_SETTINGS_UPDATED",
            resourceType = "TenantWorkdaySettings",
            resourceId = settings.id?.toString(),
            metadata = mapOf("changedFields" to changedFields),
        )
        return ApiResponse.ok(settings.toView())
    }
}
