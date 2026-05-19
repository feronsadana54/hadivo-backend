package com.hadivo.attendance.modules.subscription

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/subscriptions")
class SubscriptionController(
    private val service: SubscriptionService,
    private val guard: MembershipGuard,
) {

    @PostMapping
    fun create(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateSubscriptionRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SubscriptionView> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.create(tenantId, request).toView())
    }

    @GetMapping("/current")
    fun current(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SubscriptionView> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.currentOrThrow(tenantId).toView())
    }
}
