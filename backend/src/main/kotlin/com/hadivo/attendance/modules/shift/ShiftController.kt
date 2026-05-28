package com.hadivo.attendance.modules.shift

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class ShiftController(
    private val service: ShiftService,
    private val guard: MembershipGuard,
) {

    @GetMapping("/shifts")
    fun listShifts(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<ShiftTemplateView>> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER)
        return ApiResponse.ok(service.listShifts(tenantId))
    }

    @PostMapping("/shifts")
    fun createShift(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateShiftTemplateRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ShiftTemplateView>> {
        guard.requireAdmin(principal, tenantId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.ok(service.createShift(tenantId, principal.userId, request))
        )
    }

    @PatchMapping("/shifts/{shiftId}")
    fun updateShift(
        @PathVariable tenantId: UUID,
        @PathVariable shiftId: UUID,
        @Valid @RequestBody request: UpdateShiftTemplateRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<ShiftTemplateView> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.updateShift(tenantId, shiftId, principal.userId, request))
    }

    @GetMapping("/members/{userId}/shift-assignments")
    fun listAssignments(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<MemberShiftAssignmentView>> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.listAssignments(tenantId, userId))
    }

    @PostMapping("/members/{userId}/shift-assignments")
    fun createAssignment(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: CreateMemberShiftAssignmentRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<MemberShiftAssignmentView>> {
        guard.requireAdmin(principal, tenantId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.ok(service.createAssignment(tenantId, userId, principal.userId, request))
        )
    }

    @PatchMapping("/members/{userId}/shift-assignments/{assignmentId}")
    fun updateAssignment(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: UpdateMemberShiftAssignmentRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<MemberShiftAssignmentView> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.updateAssignment(tenantId, userId, assignmentId, principal.userId, request))
    }
}
