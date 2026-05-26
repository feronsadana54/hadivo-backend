package com.hadivo.attendance.modules.notification

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/notification-tokens")
class NotificationDeviceTokenController(
    private val service: NotificationDeviceTokenService,
    private val guard: MembershipGuard,
) {

    @PostMapping
    fun register(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: RegisterNotificationTokenRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<NotificationDeviceTokenResponse> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.register(tenantId, principal.userId, request))
    }
}
