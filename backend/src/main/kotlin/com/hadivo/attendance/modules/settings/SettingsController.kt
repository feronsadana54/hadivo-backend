package com.hadivo.attendance.modules.settings

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/attendance-settings")
class SettingsController(
    private val service: SettingsService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @GetMapping
    fun get(
        @PathVariable tenantId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SettingsView> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.get(tenantId).toView())
    }

    @PatchMapping
    fun update(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: UpdateSettingsRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<SettingsView> {
        guard.requireAdmin(principal, tenantId)
        val settings = service.update(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "ATTENDANCE_SETTINGS_UPDATED",
            resourceType = "TenantAttendanceSettings",
            resourceId = tenantId.toString(),
            metadata = mapOf("changedFields" to request.changedFields()),
        )
        return ApiResponse.ok(settings.toView())
    }
}

private fun UpdateSettingsRequest.changedFields(): List<String> = buildList {
    if (requireFaceClockIn != null) add("requireFaceClockIn")
    if (requireFaceClockOut != null) add("requireFaceClockOut")
    if (allowClockOutOutsideRadius != null) add("allowClockOutOutsideRadius")
    if (allowLateClockIn != null) add("allowLateClockIn")
    if (workStartTime != null) add("workStartTime")
    if (workEndTime != null) add("workEndTime")
    if (lateThresholdMinutes != null) add("lateThresholdMinutes")
    if (timezone != null) add("timezone")
}
