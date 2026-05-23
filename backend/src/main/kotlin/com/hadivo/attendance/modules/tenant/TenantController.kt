package com.hadivo.attendance.modules.tenant

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
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
@RequestMapping("/api/v1/tenants")
class TenantController(
    private val service: TenantService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateTenantRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<TenantView>> {
        val tenant = service.create(request, principal.userId)
        audit.log(
            tenantId = tenant.id,
            actorUserId = principal.userId,
            action = "TENANT_CREATED",
            resourceType = "Tenant",
            resourceId = tenant.id?.toString(),
            metadata = mapOf("mode" to tenant.mode.name),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(tenant.toView()))
    }

    @GetMapping("/{tenantId}")
    fun get(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<TenantView> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.get(tenantId).toView())
    }

    @PatchMapping("/{tenantId}")
    fun update(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: UpdateTenantRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<TenantView> {
        guard.requireAdmin(principal, tenantId)
        val tenant = service.update(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "TENANT_UPDATED",
            resourceType = "Tenant",
            resourceId = tenantId.toString(),
            metadata = mapOf("changedFields" to request.changedFields()),
        )
        return ApiResponse.ok(tenant.toView())
    }
}

private fun UpdateTenantRequest.changedFields(): List<String> = buildList {
    if (name != null) add("name")
    if (timezone != null) add("timezone")
    if (active != null) add("active")
}
