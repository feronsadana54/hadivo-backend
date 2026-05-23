package com.hadivo.attendance.modules.notification

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.response.PageResponse
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
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/notification-deliveries")
class NotificationController(
    private val service: NotificationService,
    private val guard: MembershipGuard,
) {

    @GetMapping
    fun deliveries(
        @PathVariable tenantId: UUID,
        @RequestParam(required = false) eventType: NotificationEventType?,
        @RequestParam(required = false) channel: NotificationChannel?,
        @RequestParam(required = false) status: NotificationDeliveryStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<PageResponse<NotificationDeliveryLogResponse>> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN)
        return ApiResponse.ok(
            service.listDeliveries(
                tenantId = tenantId,
                eventType = eventType,
                channel = channel,
                status = status,
                from = from,
                to = to,
                page = page,
                size = size,
            )
        )
    }
}
