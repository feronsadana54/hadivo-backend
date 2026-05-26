package com.hadivo.attendance.modules.reporting

import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class AttendanceReportExportService(
    private val reports: ReportingService,
    private val excelExporter: AttendanceExcelExporter,
    private val pdfExporter: AttendancePdfExporter,
) {

    fun exportExcel(tenantId: UUID, from: LocalDate, to: LocalDate): ByteArray =
        excelExporter.export(from, to, reports.exportRows(tenantId, from, to))

    fun exportPdf(tenantId: UUID, from: LocalDate, to: LocalDate): ByteArray =
        pdfExporter.export(from, to, Instant.now(), reports.exportRows(tenantId, from, to))
}
