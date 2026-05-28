package com.hadivo.attendance.modules.reporting

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Component
class AttendanceExcelExporter {

    fun export(from: LocalDate, to: LocalDate, rows: List<AttendanceReportExportRow>): ByteArray {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Attendance Report")
            val titleStyle = workbook.createCellStyle().apply {
                setFont(
                    workbook.createFont().apply {
                        bold = true
                        fontHeightInPoints = 16
                    }
                )
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(workbook.createFont().apply { bold = true })
            }

            sheet.createRow(0).createCell(0).apply {
                setCellValue("Hadivo Attendance Report")
                cellStyle = titleStyle
            }
            sheet.createRow(1).createCell(0).setCellValue("Period: $from to $to")

            val headerRow = sheet.createRow(3)
            AttendanceReportExportColumns.headers.forEachIndexed { columnIndex, header ->
                headerRow.createCell(columnIndex).apply {
                    setCellValue(header)
                    cellStyle = headerStyle
                }
            }
            sheet.createFreezePane(0, 4)

            rows.forEachIndexed { rowIndex, reportRow ->
                val row = sheet.createRow(rowIndex + 4)
                row.createCell(0).setCellValue(reportRow.date.toString())
                row.createCell(1).setCellValue(reportRow.userId.toString())
                row.createCell(2).setCellValue(reportRow.fullName)
                row.createCell(3).setCellValue(reportRow.email)
                row.createCell(4).setCellValue(reportRow.status.name)
                row.createCell(5).setCellValue(reportRow.clockInTime)
                row.createCell(6).setCellValue(reportRow.clockOutTime)
                reportRow.workDurationMinutes
                    ?.let { row.createCell(7).setCellValue(it.toDouble()) }
                    ?: row.createCell(7).setCellValue("")
                row.createCell(8).setCellValue(reportRow.clockOutOutsideRadius.toString())
                row.createCell(9).setCellValue(reportRow.shiftName)
                row.createCell(10).setCellValue(reportRow.scheduledStartTime)
                row.createCell(11).setCellValue(reportRow.scheduledEndTime)
                reportRow.lateThresholdMinutes
                    ?.let { row.createCell(12).setCellValue(it.toDouble()) }
                    ?: row.createCell(12).setCellValue("")
            }

            AttendanceReportExportColumns.headers.indices.forEach(sheet::autoSizeColumn)

            return ByteArrayOutputStream().use { output ->
                workbook.write(output)
                output.toByteArray()
            }
        }
    }
}
