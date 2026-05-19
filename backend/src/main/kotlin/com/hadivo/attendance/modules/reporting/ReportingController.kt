package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/reports/attendance")
class ReportingController(
    private val service: ReportingService,
    private val guard: MembershipGuard,
) {

    @GetMapping("/daily")
    fun daily(
        @PathVariable tenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<DailyReport> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        return ApiResponse.ok(service.daily(tenantId, date))
    }

    @GetMapping("/monthly")
    fun monthly(
        @PathVariable tenantId: UUID,
        @RequestParam month: String,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<MonthlyReport> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        return ApiResponse.ok(service.monthly(tenantId, YearMonth.parse(month)))
    }
}
