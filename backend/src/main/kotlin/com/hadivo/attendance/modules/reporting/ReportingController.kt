package com.hadivo.attendance.modules.reporting

import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/reports/attendance")
class ReportingController(
    private val service: ReportingService,
    private val exports: AttendanceReportExportService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @GetMapping("/daily")
    fun daily(
        @PathVariable tenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<DailyReport> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        return ApiResponse.ok(service.daily(tenantId, date))
    }

    @GetMapping("/monthly")
    fun monthly(
        @PathVariable tenantId: UUID,
        @RequestParam month: String,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<MonthlyReport> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        return ApiResponse.ok(service.monthly(tenantId, YearMonth.parse(month)))
    }

    @GetMapping("/export.csv", produces = ["text/csv"])
    fun exportCsv(
        @PathVariable tenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<String> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        val filename = "hadivo-attendance-report-$from-to-$to.csv"
        val csv = service.exportCsv(tenantId, from, to)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "REPORT_CSV_EXPORTED",
            resourceType = "AttendanceReport",
            metadata = mapOf("from" to from.toString(), "to" to to.toString()),
        )
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(csv)
    }

    @GetMapping(
        "/export.xlsx",
        produces = ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"],
    )
    fun exportExcel(
        @PathVariable tenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ByteArray> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        val filename = "hadivo-attendance-report-$from-to-$to.xlsx"
        val workbook = exports.exportExcel(tenantId, from, to)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "REPORT_EXCEL_EXPORTED",
            resourceType = "AttendanceReport",
            metadata = mapOf("from" to from.toString(), "to" to to.toString()),
        )
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(workbook)
    }

    @GetMapping("/export.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun exportPdf(
        @PathVariable tenantId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @CurrentUser principal: AuthPrincipal,
    ): ResponseEntity<ByteArray> {
        guard.requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN, Role.MANAGER, Role.TEACHER)
        val filename = "hadivo-attendance-report-$from-to-$to.pdf"
        val pdf = exports.exportPdf(tenantId, from, to)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "REPORT_PDF_EXPORTED",
            resourceType = "AttendanceReport",
            metadata = mapOf("from" to from.toString(), "to" to to.toString()),
        )
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(pdf)
    }
}
