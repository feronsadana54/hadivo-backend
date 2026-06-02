package com.hadivo.attendance.modules.leave.balance

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class LeaveBalanceController(
    private val service: LeaveBalanceService,
    private val guard: MembershipGuard,
) {

    @GetMapping("/leave-balances")
    fun list(
        @PathVariable tenantId: UUID,
        @RequestParam(required = false) year: Int?,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<LeaveBalanceView>> {
        guard.requireAdmin(principal, tenantId)
        val resolvedYear = year ?: LocalDate.now().year
        return ApiResponse.ok(service.listByTenantAndYear(tenantId, resolvedYear))
    }

    @GetMapping("/members/{userId}/leave-balance")
    fun getMember(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @RequestParam(required = false) year: Int?,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LeaveBalanceView> {
        val membership = guard.requireMember(principal, tenantId)
        if (membership.role !in ADMIN_ROLES && userId != principal.userId) {
            throw DomainException.forbidden("Hanya dapat melihat saldo cuti milik sendiri")
        }
        val resolvedYear = year ?: LocalDate.now().year
        return ApiResponse.ok(service.getView(tenantId, userId, resolvedYear))
    }

    @PostMapping("/members/{userId}/leave-balance/adjust")
    fun adjust(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: AdjustLeaveBalanceRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LeaveBalanceView> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.adjust(tenantId, userId, request, principal.userId))
    }

    companion object {
        val ADMIN_ROLES = setOf(Role.TENANT_ADMIN, Role.SUPER_ADMIN)
    }
}
