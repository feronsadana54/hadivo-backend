package com.hadivo.attendance.modules.device

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/members/{userId}/devices")
class DeviceController(
    private val service: DeviceBindingService,
    private val guard: MembershipGuard,
) {

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<DeviceView>> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.listDevices(tenantId, userId))
    }

    @PostMapping("/reset")
    fun reset(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<DeviceView>> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.resetDevices(tenantId, userId, principal.userId))
    }
}
