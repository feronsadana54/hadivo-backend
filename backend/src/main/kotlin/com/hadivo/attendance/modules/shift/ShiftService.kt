package com.hadivo.attendance.modules.shift

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class ShiftService(
    private val shifts: ShiftTemplateRepository,
    private val assignments: MemberShiftAssignmentRepository,
    private val memberships: MembershipRepository,
    private val audit: AuditLogger,
) {

    @Transactional(readOnly = true)
    fun listShifts(tenantId: UUID): List<ShiftTemplateView> =
        shifts.findAllByTenantIdOrderByActiveDescStartTimeAscNameAsc(tenantId).map { it.toView() }

    @Transactional
    fun createShift(tenantId: UUID, actorUserId: UUID, request: CreateShiftTemplateRequest): ShiftTemplateView {
        val shift = shifts.save(
            ShiftTemplate(
                tenantId = tenantId,
                name = cleanName(request.name),
                startTime = request.startTime ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Jam mulai wajib diisi"),
                endTime = request.endTime ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Jam selesai wajib diisi"),
                lateThresholdMinutes = request.lateThresholdMinutes,
                allowsOvertime = request.allowsOvertime,
                active = request.active,
            )
        )
        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "SHIFT_CREATED",
            resourceType = "ShiftTemplate",
            resourceId = shift.id?.toString(),
            metadata = shift.auditMetadata(),
        )
        return shift.toView()
    }

    @Transactional
    fun updateShift(
        tenantId: UUID,
        shiftId: UUID,
        actorUserId: UUID,
        request: UpdateShiftTemplateRequest,
    ): ShiftTemplateView {
        val shift = shifts.findByIdAndTenantId(shiftId, tenantId)
            ?: throw DomainException.notFound("Shift", shiftId)

        request.name?.let { shift.name = cleanName(it) }
        request.startTime?.let { shift.startTime = it }
        request.endTime?.let { shift.endTime = it }
        request.lateThresholdMinutes?.let { shift.lateThresholdMinutes = it }
        request.allowsOvertime?.let { shift.allowsOvertime = it }
        request.active?.let { shift.active = it }

        val saved = shifts.save(shift)
        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "SHIFT_UPDATED",
            resourceType = "ShiftTemplate",
            resourceId = saved.id?.toString(),
            metadata = request.changedFields().associateWith { true },
        )
        return saved.toView()
    }

    @Transactional(readOnly = true)
    fun listAssignments(tenantId: UUID, userId: UUID, today: LocalDate = LocalDate.now()): List<MemberShiftAssignmentView> {
        requireMember(tenantId, userId)
        val rows = assignments.findAllByTenantIdAndUserIdOrderByEffectiveFromDescCreatedAtDesc(tenantId, userId)
        val shiftMap = shifts.findAllById(rows.map { it.shiftTemplateId }.distinct()).associateBy { it.id }
        return rows.map { it.toView(shiftMap[it.shiftTemplateId], today) }
    }

    @Transactional
    fun createAssignment(
        tenantId: UUID,
        userId: UUID,
        actorUserId: UUID,
        request: CreateMemberShiftAssignmentRequest,
    ): MemberShiftAssignmentView {
        requireMember(tenantId, userId)
        val shift = requireShift(tenantId, request.shiftTemplateId)
        if (!shift.active) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Shift tidak aktif")
        }
        val effectiveFrom = request.effectiveFrom
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal mulai assignment wajib diisi")
        validateDateRange(effectiveFrom, request.effectiveTo)
        if (request.active) {
            validateNoOverlap(tenantId, userId, effectiveFrom, request.effectiveTo, excludeId = null)
        }

        val assignment = assignments.save(
            MemberShiftAssignment(
                tenantId = tenantId,
                userId = userId,
                shiftTemplateId = shift.id ?: error("shift id null"),
                effectiveFrom = effectiveFrom,
                effectiveTo = request.effectiveTo,
                active = request.active,
            )
        )
        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "SHIFT_ASSIGNMENT_CREATED",
            resourceType = "MemberShiftAssignment",
            resourceId = assignment.id?.toString(),
            metadata = assignment.auditMetadata(shift),
        )
        return assignment.toView(shift, LocalDate.now())
    }

    @Transactional
    fun updateAssignment(
        tenantId: UUID,
        userId: UUID,
        assignmentId: UUID,
        actorUserId: UUID,
        request: UpdateMemberShiftAssignmentRequest,
    ): MemberShiftAssignmentView {
        requireMember(tenantId, userId)
        val assignment = assignments.findByIdAndTenantIdAndUserId(assignmentId, tenantId, userId)
            ?: throw DomainException.notFound("Shift assignment", assignmentId)

        val shift = request.shiftTemplateId?.let { requireShift(tenantId, it) }
            ?: requireShift(tenantId, assignment.shiftTemplateId)
        request.shiftTemplateId?.let { assignment.shiftTemplateId = it }
        request.effectiveFrom?.let { assignment.effectiveFrom = it }
        request.effectiveTo?.let { assignment.effectiveTo = it }
        request.active?.let { assignment.active = it }
        validateDateRange(assignment.effectiveFrom, assignment.effectiveTo)
        if (assignment.active) {
            validateNoOverlap(tenantId, userId, assignment.effectiveFrom, assignment.effectiveTo, assignment.id)
        }

        val saved = assignments.save(assignment)
        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "SHIFT_ASSIGNMENT_UPDATED",
            resourceType = "MemberShiftAssignment",
            resourceId = saved.id?.toString(),
            metadata = request.changedFields().associateWith { true },
        )
        return saved.toView(shift, LocalDate.now())
    }

    private fun requireMember(tenantId: UUID, userId: UUID) {
        val membership = memberships.findByTenantIdAndUserId(tenantId, userId)
            ?: throw DomainException.notFound("Member", userId)
        if (!membership.active) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Member tidak aktif")
        }
    }

    private fun requireShift(tenantId: UUID, shiftId: UUID?): ShiftTemplate {
        val id = shiftId ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Shift wajib dipilih")
        return shifts.findByIdAndTenantId(id, tenantId)
            ?: throw DomainException.notFound("Shift", id)
    }

    private fun cleanName(value: String?): String {
        val name = value?.trim().orEmpty()
        if (name.isBlank()) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Nama shift wajib diisi")
        }
        return name
    }

    private fun validateDateRange(effectiveFrom: LocalDate, effectiveTo: LocalDate?) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal selesai assignment tidak boleh sebelum tanggal mulai")
        }
    }

    private fun validateNoOverlap(
        tenantId: UUID,
        userId: UUID,
        effectiveFrom: LocalDate,
        effectiveTo: LocalDate?,
        excludeId: UUID?,
    ) {
        val overlaps = assignments.findActiveOverlaps(
            tenantId = tenantId,
            userId = userId,
            effectiveFrom = effectiveFrom,
            effectiveToLimit = effectiveTo ?: FAR_FUTURE,
            excludeId = excludeId,
        )
        if (overlaps.isNotEmpty()) {
            throw DomainException.conflict("Assignment shift aktif tidak boleh overlap untuk member yang sama")
        }
    }

    private fun ShiftTemplate.auditMetadata(): Map<String, Any?> =
        mapOf(
            "name" to name,
            "startTime" to startTime.toString(),
            "endTime" to endTime.toString(),
            "lateThresholdMinutes" to lateThresholdMinutes,
            "allowsOvertime" to allowsOvertime,
            "active" to active,
            "overnight" to isOvernight(),
        )

    private fun MemberShiftAssignment.auditMetadata(shift: ShiftTemplate): Map<String, Any?> =
        mapOf(
            "userId" to userId.toString(),
            "shiftTemplateId" to shift.id?.toString(),
            "shiftName" to shift.name,
            "effectiveFrom" to effectiveFrom.toString(),
            "effectiveTo" to effectiveTo?.toString(),
            "active" to active,
        )

    private fun UpdateShiftTemplateRequest.changedFields(): List<String> = buildList {
        if (name != null) add("name")
        if (startTime != null) add("startTime")
        if (endTime != null) add("endTime")
        if (lateThresholdMinutes != null) add("lateThresholdMinutes")
        if (allowsOvertime != null) add("allowsOvertime")
        if (active != null) add("active")
    }

    private fun UpdateMemberShiftAssignmentRequest.changedFields(): List<String> = buildList {
        if (shiftTemplateId != null) add("shiftTemplateId")
        if (effectiveFrom != null) add("effectiveFrom")
        if (effectiveTo != null) add("effectiveTo")
        if (active != null) add("active")
    }

    private companion object {
        val FAR_FUTURE: LocalDate = LocalDate.of(9999, 12, 31)
    }
}
