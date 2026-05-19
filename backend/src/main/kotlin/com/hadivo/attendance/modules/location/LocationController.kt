package com.hadivo.attendance.modules.location

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
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
@RequestMapping("/api/v1/tenants/{tenantId}/locations")
class LocationController(
    private val service: LocationService,
    private val guard: MembershipGuard,
) {

    @PostMapping
    fun create(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateLocationRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<LocationView>> {
        guard.requireAdmin(principal, tenantId)
        val location = service.create(tenantId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(location.toView()))
    }

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<LocationView>> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.list(tenantId).map { it.toView() })
    }

    @PatchMapping("/{locationId}")
    fun update(
        @PathVariable tenantId: UUID,
        @PathVariable locationId: UUID,
        @Valid @RequestBody request: UpdateLocationRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LocationView> {
        guard.requireAdmin(principal, tenantId)
        return ApiResponse.ok(service.update(tenantId, locationId, request).toView())
    }
}
