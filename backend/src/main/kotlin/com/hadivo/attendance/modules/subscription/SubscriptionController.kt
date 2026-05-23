package com.hadivo.attendance.modules.subscription

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
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
    private val audit: AuditLogger,
) {

    @PostMapping
    fun create(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateSubscriptionRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SubscriptionView> {
        guard.requireAdmin(principal, tenantId)
        val subscription = service.create(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "SUBSCRIPTION_UPDATED",
            resourceType = "Subscription",
            resourceId = subscription.id?.toString(),
            metadata = mapOf("plan" to subscription.plan.name, "status" to subscription.status.name),
        )
        return ApiResponse.ok(subscription.toView())
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
