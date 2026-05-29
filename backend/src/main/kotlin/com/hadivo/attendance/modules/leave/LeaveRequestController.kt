package com.hadivo.attendance.modules.leave

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/leave-requests")
class LeaveRequestController(
    private val service: LeaveRequestService,
) {

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<LeaveRequestView>> = ApiResponse.ok(service.list(principal, tenantId))

    @PostMapping
    fun create(
        @PathVariable tenantId: UUID,
        @RequestBody payload: CreateLeaveRequestRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<LeaveRequestView>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.ok(service.create(principal, tenantId, payload))
        )

    @GetMapping("/{requestId}")
    fun get(
        @PathVariable tenantId: UUID,
        @PathVariable requestId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LeaveRequestView> = ApiResponse.ok(service.get(principal, tenantId, requestId))

    @PostMapping("/{requestId}/approve")
    fun approve(
        @PathVariable tenantId: UUID,
        @PathVariable requestId: UUID,
        @RequestBody(required = false) payload: ReviewLeaveRequestRequest?,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LeaveRequestView> =
        ApiResponse.ok(service.approve(principal, tenantId, requestId, payload ?: ReviewLeaveRequestRequest()))

    @PostMapping("/{requestId}/reject")
    fun reject(
        @PathVariable tenantId: UUID,
        @PathVariable requestId: UUID,
        @RequestBody(required = false) payload: ReviewLeaveRequestRequest?,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LeaveRequestView> =
        ApiResponse.ok(service.reject(principal, tenantId, requestId, payload ?: ReviewLeaveRequestRequest()))

    @PostMapping("/{requestId}/cancel")
    fun cancel(
        @PathVariable tenantId: UUID,
        @PathVariable requestId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<LeaveRequestView> = ApiResponse.ok(service.cancel(principal, tenantId, requestId))
}
