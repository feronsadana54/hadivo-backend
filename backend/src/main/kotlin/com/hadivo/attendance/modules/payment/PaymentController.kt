package com.hadivo.attendance.modules.payment

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class PaymentController(
    private val service: PaymentService,
    private val guard: MembershipGuard,
) {

    @GetMapping("/subscription-packages")
    fun packages(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<SubscriptionPackageView>> {
        requirePaymentRole(principal, tenantId)
        return ApiResponse.ok(service.listPackages())
    }

    @PostMapping("/subscription-payments")
    fun createPayment(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateSubscriptionPaymentRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<PaymentView> {
        requirePaymentRole(principal, tenantId)
        return ApiResponse.ok(service.createPayment(tenantId, principal.userId, request))
    }

    @GetMapping("/subscription-payments")
    fun listPayments(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<PaymentView>> {
        requirePaymentRole(principal, tenantId)
        return ApiResponse.ok(service.listPayments(tenantId))
    }

    @GetMapping("/subscription-payments/{paymentId}")
    fun getPayment(
        @PathVariable tenantId: UUID,
        @PathVariable paymentId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<PaymentView> {
        requirePaymentRole(principal, tenantId)
        return ApiResponse.ok(service.getPayment(tenantId, paymentId))
    }

    private fun requirePaymentRole(principal: AuthPrincipal, tenantId: UUID) {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN)
    }
}
