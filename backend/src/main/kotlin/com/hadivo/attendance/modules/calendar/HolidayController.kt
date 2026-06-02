package com.hadivo.attendance.modules.calendar

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/holidays")
class HolidayController(
    private val service: HolidayService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @GetMapping
    fun list(
        @PathVariable tenantId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<List<HolidayView>> {
        guard.requireMember(principal, tenantId)
        return ApiResponse.ok(service.list(tenantId, from, to).map { it.toView() })
    }

    @PostMapping
    fun create(
        @PathVariable tenantId: UUID,
        @RequestBody request: CreateHolidayRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<HolidayView>> {
        guard.requireAdmin(principal, tenantId)
        val saved = service.create(tenantId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "HOLIDAY_CREATED",
            resourceType = "TenantHoliday",
            resourceId = saved.id?.toString(),
            metadata = mapOf(
                "holidayDate" to saved.holidayDate.toString(),
                "type" to saved.type.name,
                "active" to saved.active,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved.toView()))
    }

    @PatchMapping("/{holidayId}")
    fun update(
        @PathVariable tenantId: UUID,
        @PathVariable holidayId: UUID,
        @RequestBody request: UpdateHolidayRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<HolidayView> {
        guard.requireAdmin(principal, tenantId)
        val (saved, changedFields) = service.update(tenantId, holidayId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "HOLIDAY_UPDATED",
            resourceType = "TenantHoliday",
            resourceId = saved.id?.toString(),
            metadata = mapOf(
                "holidayId" to holidayId.toString(),
                "changedFields" to changedFields,
            ),
        )
        return ApiResponse.ok(saved.toView())
    }
}
