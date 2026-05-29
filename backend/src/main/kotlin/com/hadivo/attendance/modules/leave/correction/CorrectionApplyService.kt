package com.hadivo.attendance.modules.leave.correction

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.util.TimeUtils
import com.hadivo.attendance.modules.attendance.AttendanceRecord
import com.hadivo.attendance.modules.attendance.AttendanceRecordRepository
import com.hadivo.attendance.modules.attendance.AttendanceStatus
import com.hadivo.attendance.modules.attendance.AttendanceStatusCalculator
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.leave.LeaveRequest
import com.hadivo.attendance.modules.settings.SettingsService
import com.hadivo.attendance.modules.shift.ShiftScheduleResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Service
class CorrectionApplyService(
    private val applies: AttendanceCorrectionApplyRepository,
    private val records: AttendanceRecordRepository,
    private val settingsService: SettingsService,
    private val shiftResolver: ShiftScheduleResolver,
    private val statusCalculator: AttendanceStatusCalculator,
    private val audit: AuditLogger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Apply approved ATTENDANCE_CORRECTION request to attendance_records.
     * Must be called inside the approver's transaction so that a failure
     * rolls back the leave approval and leaves the request in PENDING state.
     *
     * Idempotent: if an apply row already exists for the leave request, the
     * existing row is returned and audited as already-applied without any
     * mutation to attendance_records.
     */
    @Transactional
    fun apply(request: LeaveRequest, reviewerUserId: UUID): AttendanceCorrectionApply {
        val leaveRequestId = request.id
            ?: error("leave request id null when applying correction")

        applies.findByLeaveRequestId(leaveRequestId)?.let { existing ->
            audit.log(
                tenantId = request.tenantId,
                actorUserId = reviewerUserId,
                action = "ATTENDANCE_CORRECTION_ALREADY_APPLIED",
                resourceType = "LeaveRequest",
                resourceId = leaveRequestId.toString(),
                metadata = mapOf("attendanceRecordId" to existing.attendanceRecordId?.toString()),
            )
            return existing
        }

        try {
            val settings = settingsService.get(request.tenantId)
            val schedule = shiftResolver.resolve(
                tenantId = request.tenantId,
                userId = request.requesterUserId,
                settings = settings,
                now = request.startDate.atTime(LocalTime.NOON).atZone(TimeUtils.zone(settings.timezone)),
            )

            val requestedIn = request.requestedClockInAt
            val requestedOut = request.requestedClockOutAt
            val existingRecord = records.findByTenantIdAndUserIdAndDate(
                request.tenantId,
                request.requesterUserId,
                request.startDate,
            )

            val result = if (existingRecord != null) {
                applyToExistingRecord(
                    request = request,
                    record = existingRecord,
                    schedule = schedule,
                    requestedClockIn = requestedIn,
                    requestedClockOut = requestedOut,
                    reviewerUserId = reviewerUserId,
                )
            } else {
                createCorrectionRecord(
                    request = request,
                    schedule = schedule,
                    requestedClockIn = requestedIn,
                    requestedClockOut = requestedOut,
                    reviewerUserId = reviewerUserId,
                )
            }

            audit.log(
                tenantId = request.tenantId,
                actorUserId = reviewerUserId,
                action = "ATTENDANCE_CORRECTION_APPLIED",
                resourceType = "AttendanceRecord",
                resourceId = result.attendanceRecordId?.toString(),
                metadata = mapOf(
                    "leaveRequestId" to leaveRequestId.toString(),
                    "attendanceRecordId" to result.attendanceRecordId?.toString(),
                    "originalClockInExists" to (result.originalClockInAt != null),
                    "originalClockOutExists" to (result.originalClockOutAt != null),
                    "appliedClockInProvided" to (result.appliedClockInAt != null),
                    "appliedClockOutProvided" to (result.appliedClockOutAt != null),
                    "originalStatus" to result.originalStatus?.name,
                    "appliedStatus" to result.appliedStatus.name,
                    "recordCreatedByCorrection" to result.recordCreatedByCorrection,
                ),
            )

            return result
        } catch (ex: DomainException) {
            audit.log(
                tenantId = request.tenantId,
                actorUserId = reviewerUserId,
                action = "ATTENDANCE_CORRECTION_APPLY_FAILED",
                resourceType = "LeaveRequest",
                resourceId = leaveRequestId.toString(),
                metadata = mapOf(
                    "errorCode" to ex.code.name,
                    "reason" to (ex.message ?: ex.code.defaultMessage),
                ),
            )
            throw ex
        } catch (ex: Exception) {
            log.warn("Correction apply failed for leaveRequest={}: {}", leaveRequestId, ex.message)
            audit.log(
                tenantId = request.tenantId,
                actorUserId = reviewerUserId,
                action = "ATTENDANCE_CORRECTION_APPLY_FAILED",
                resourceType = "LeaveRequest",
                resourceId = leaveRequestId.toString(),
                metadata = mapOf(
                    "errorCode" to "INTERNAL",
                    "reason" to (ex.message ?: "Tidak dapat menerapkan koreksi"),
                ),
            )
            throw DomainException(
                ErrorCode.UNPROCESSABLE,
                "Tidak dapat menerapkan koreksi absensi. Coba lagi atau hubungi admin.",
            )
        }
    }

    private fun applyToExistingRecord(
        request: LeaveRequest,
        record: AttendanceRecord,
        schedule: com.hadivo.attendance.modules.shift.ResolvedShiftSchedule,
        requestedClockIn: Instant?,
        requestedClockOut: Instant?,
        reviewerUserId: UUID,
    ): AttendanceCorrectionApply {
        val originalClockIn = record.clockInAt
        val originalClockOut = record.clockOutAt
        val originalStatus = record.status
        val originalDuration = record.workDurationMinutes

        val newClockIn = requestedClockIn ?: originalClockIn
        val newClockOut = requestedClockOut ?: originalClockOut
        val newStatus = statusCalculator.resolve(schedule, newClockIn, newClockOut, originalStatus)
        val newDuration = statusCalculator.durationMinutes(newClockIn, newClockOut)
        val now = Instant.now()
        val correctionNote = request.correctionNote ?: request.reason

        record.clockInAt = newClockIn
        record.clockOutAt = newClockOut
        record.status = newStatus
        record.workDurationMinutes = newDuration
        record.correctionApplied = true
        record.correctionRequestId = request.id
        record.correctedBy = reviewerUserId
        record.correctedAt = now
        record.correctionNote = correctionNote
        val savedRecord = records.save(record)

        return applies.save(
            AttendanceCorrectionApply(
                tenantId = request.tenantId,
                leaveRequestId = request.id ?: error("leave request id null"),
                attendanceRecordId = savedRecord.id,
                requesterUserId = request.requesterUserId,
                reviewerUserId = reviewerUserId,
                appliedBy = reviewerUserId,
                originalClockInAt = originalClockIn,
                originalClockOutAt = originalClockOut,
                appliedClockInAt = newClockIn,
                appliedClockOutAt = newClockOut,
                originalStatus = originalStatus,
                appliedStatus = newStatus,
                originalWorkDurationMinutes = originalDuration,
                appliedWorkDurationMinutes = newDuration,
                correctionReason = correctionNote,
                recordCreatedByCorrection = false,
            ).also { it.appliedAt = now }
        )
    }

    private fun createCorrectionRecord(
        request: LeaveRequest,
        schedule: com.hadivo.attendance.modules.shift.ResolvedShiftSchedule,
        requestedClockIn: Instant?,
        requestedClockOut: Instant?,
        reviewerUserId: UUID,
    ): AttendanceCorrectionApply {
        val initialStatus = AttendanceStatus.ON_TIME
        val newStatus = statusCalculator.resolve(schedule, requestedClockIn, requestedClockOut, initialStatus)
        val newDuration = statusCalculator.durationMinutes(requestedClockIn, requestedClockOut)
        val now = Instant.now()
        val correctionNote = request.correctionNote ?: request.reason

        val created = records.save(
            AttendanceRecord(
                tenantId = request.tenantId,
                userId = request.requesterUserId,
                date = request.startDate,
                clockInAt = requestedClockIn,
                clockOutAt = requestedClockOut,
                clockInLocationId = null,
                clockOutLocationId = null,
                clockInLatitude = null,
                clockInLongitude = null,
                clockOutLatitude = null,
                clockOutLongitude = null,
                clockInDeviceId = null,
                clockOutDeviceId = null,
                clockOutOutsideRadius = false,
                status = newStatus,
                workDurationMinutes = newDuration,
                shiftTemplateId = schedule.shiftId,
                shiftName = schedule.shiftName,
                scheduledStartTime = schedule.startTime,
                scheduledEndTime = schedule.endTime,
                lateThresholdMinutes = schedule.lateThresholdMinutes,
                correctionApplied = true,
                correctionRequestId = request.id,
                correctedBy = reviewerUserId,
                correctedAt = now,
                correctionNote = correctionNote,
            )
        )

        return applies.save(
            AttendanceCorrectionApply(
                tenantId = request.tenantId,
                leaveRequestId = request.id ?: error("leave request id null"),
                attendanceRecordId = created.id,
                requesterUserId = request.requesterUserId,
                reviewerUserId = reviewerUserId,
                appliedBy = reviewerUserId,
                originalClockInAt = null,
                originalClockOutAt = null,
                appliedClockInAt = requestedClockIn,
                appliedClockOutAt = requestedClockOut,
                originalStatus = null,
                appliedStatus = newStatus,
                originalWorkDurationMinutes = null,
                appliedWorkDurationMinutes = newDuration,
                correctionReason = correctionNote,
                recordCreatedByCorrection = true,
            ).also { it.appliedAt = now }
        )
    }
}
