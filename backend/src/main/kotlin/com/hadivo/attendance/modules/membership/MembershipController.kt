package com.hadivo.attendance.modules.membership

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/memberships")
class MembershipController(
    private val service: MembershipService,
    private val guard: MembershipGuard,
) {

    @PostMapping
    fun add(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateMembershipRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<MembershipView>> {
        guard.requireAdmin(principal, tenantId)
        val membership = service.add(tenantId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(membership.toView()))
    }

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<MembershipView>> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.list(tenantId).map { it.toView() })
    }

    @DeleteMapping("/{membershipId}")
    fun remove(
        @PathVariable tenantId: UUID,
        @PathVariable membershipId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        guard.requireAdmin(principal, tenantId)
        service.remove(tenantId, membershipId)
        return ResponseEntity.noContent().build()
    }
}
