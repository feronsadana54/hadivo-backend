package com.hadivo.attendance.modules.membership

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
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
    private val audit: AuditLogger,
) {

    @PostMapping
    fun add(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateMembershipRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<MembershipView>> {
        guard.requireAdmin(principal, tenantId)
        val membership = service.add(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "MEMBER_ADDED",
            resourceType = "Membership",
            resourceId = membership.id?.toString(),
            metadata = mapOf("userId" to request.userId.toString(), "role" to request.role.name),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(membership.toView()))
    }

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<MembershipResponse>> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.listResponses(tenantId))
    }

    @DeleteMapping("/{membershipId}")
    fun remove(
        @PathVariable tenantId: UUID,
        @PathVariable membershipId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        guard.requireAdmin(principal, tenantId)
        service.remove(tenantId, membershipId)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "MEMBER_REMOVED",
            resourceType = "Membership",
            resourceId = membershipId.toString(),
        )
        return ResponseEntity.noContent().build()
    }
}
