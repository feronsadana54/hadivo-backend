package com.hadivo.attendance.modules.attendance

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class AttendanceController(
    private val service: AttendanceService,
    private val guard: MembershipGuard,
) {

    @PostMapping("/attendance/clock-in")
    fun clockIn(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: ClockInRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<AttendanceRecordView>> {
        guard.requireMember(principal, tenantId)
        val record = service.clockIn(tenantId, principal.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(record.toView()))
    }

    @PostMapping("/attendance/clock-out")
    fun clockOut(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: ClockOutRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<AttendanceRecordView> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.clockOut(tenantId, principal.userId, request).toView())
    }

    @GetMapping("/attendance/me/today")
    fun today(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<AttendanceRecordView?> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.todayFor(tenantId, principal.userId)?.toView())
    }

    @GetMapping("/attendance/me")
    fun history(
        @PathVariable tenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<AttendanceRecordView>> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.history(tenantId, principal.userId, from, to).map { it.toView() })
    }

    @GetMapping("/attendance-attempts")
    fun attempts(
        @PathVariable tenantId: UUID,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<AttendanceAttemptView>> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        return ApiResponse.ok(service.listAttempts(tenantId, userId, from, to))
    }
}
