package com.hadivo.attendance.modules.parentlink

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
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
@RequestMapping("/api/v1/tenants/{tenantId}/parent-links")
class ParentLinkController(
    private val service: ParentLinkService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @PostMapping
    fun create(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateParentLinkRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<ParentLinkView>> {
        guard.requireAdmin(principal, tenantId)
        val link = service.create(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "PARENT_LINK_CREATED",
            resourceType = "ParentStudentLink",
            resourceId = link.id?.toString(),
            metadata = mapOf(
                "parentUserId" to request.parentUserId.toString(),
                "studentUserId" to request.studentUserId.toString(),
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(link.toView()))
    }

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<ParentLinkView>> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.list(tenantId).map { it.toView() })
    }

    @DeleteMapping("/{linkId}")
    fun remove(
        @PathVariable tenantId: UUID,
        @PathVariable linkId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        guard.requireAdmin(principal, tenantId)
        service.remove(tenantId, linkId)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "PARENT_LINK_REMOVED",
            resourceType = "ParentStudentLink",
            resourceId = linkId.toString(),
        )
        return ResponseEntity.noContent().build()
    }
}
