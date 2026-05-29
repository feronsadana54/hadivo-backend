package com.hadivo.attendance.modules.reporting

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class AttendancePdfExporter {

    fun export(from: LocalDate, to: LocalDate, generatedAt: Instant, rows: List<AttendanceReportExportRow>): ByteArray {
        val output = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate(), 24f, 24f, 24f, 24f)
        PdfWriter.getInstance(document, output)

        document.open()
        document.add(
            Paragraph("Hadivo Attendance Report", titleFont).apply {
                spacingAfter = 8f
            }
        )
        document.add(Paragraph("Period: $from to $to", bodyFont))
        document.add(
            Paragraph("Generated at: ${generatedAtFormatter.format(generatedAt)}", smallFont).apply {
                spacingAfter = 12f
            }
        )

        if (rows.isEmpty()) {
            document.add(Paragraph("No attendance data for this period.", bodyFont))
        } else {
            document.add(buildTable(rows))
        }

        document.close()
        return output.toByteArray()
    }

    private fun buildTable(rows: List<AttendanceReportExportRow>): PdfPTable =
        PdfPTable(AttendanceReportExportColumns.headers.size).apply {
            widthPercentage = 100f
            setWidths(
                floatArrayOf(
                    1.1f, 2.1f, 1.8f, 2.2f, 1.3f, 1.7f, 1.7f, 1.4f, 1.3f, 1.6f, 1.2f, 1.2f, 1.2f, 1.3f, 1.2f, 1.3f, 2.1f,
                )
            )

            AttendanceReportExportColumns.headers.forEach { addCell(headerCell(it)) }

            rows.forEach { row ->
                addCell(bodyCell(row.date.toString()))
                addCell(bodyCell(row.userId.toString()))
                addCell(bodyCell(row.fullName))
                addCell(bodyCell(row.email))
                addCell(bodyCell(row.status))
                addCell(bodyCell(row.clockInTime))
                addCell(bodyCell(row.clockOutTime))
                addCell(bodyCell(row.workDurationMinutes?.toString().orEmpty()))
                addCell(bodyCell(row.clockOutOutsideRadius.toString()))
                addCell(bodyCell(row.shiftName))
                addCell(bodyCell(row.scheduledStartTime))
                addCell(bodyCell(row.scheduledEndTime))
                addCell(bodyCell(row.lateThresholdMinutes?.toString().orEmpty()))
                addCell(bodyCell(row.leaveType))
                addCell(bodyCell(row.leaveStatus))
                addCell(bodyCell(row.correctionApplied))
                addCell(bodyCell(row.correctionRequestId))
            }
        }

    private fun headerCell(value: String): PdfPCell =
        PdfPCell(Phrase(value, headerFont)).apply {
            backgroundColor = Color(235, 238, 242)
            horizontalAlignment = Element.ALIGN_LEFT
            verticalAlignment = Element.ALIGN_MIDDLE
            setPadding(4f)
        }

    private fun bodyCell(value: String): PdfPCell =
        PdfPCell(Phrase(value, smallFont)).apply {
            horizontalAlignment = Element.ALIGN_LEFT
            verticalAlignment = Element.ALIGN_MIDDLE
            setPadding(4f)
        }

    private companion object {
        val titleFont: Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
        val bodyFont: Font = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val smallFont: Font = FontFactory.getFont(FontFactory.HELVETICA, 8f)
        val headerFont: Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f)
        val generatedAtFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    }
}
