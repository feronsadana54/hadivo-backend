package com.hadivo.attendance.modules.settings

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/attendance-settings")
class SettingsController(
    private val service: SettingsService,
    private val guard: MembershipGuard,
) {

    @GetMapping
    fun get(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SettingsView> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.get(tenantId).toView())
    }

    @PatchMapping
    fun update(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: UpdateSettingsRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SettingsView> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.update(tenantId, request).toView())
    }
}
