package com.hadivo.attendance.modules.shift

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateShiftTemplateRequest(
    @field:NotBlank val name: String?,
    @field:NotNull val startTime: LocalTime?,
    @field:NotNull val endTime: LocalTime?,
    @field:Min(0) @field:Max(240) val lateThresholdMinutes: Int = 0,
    val allowsOvertime: Boolean = false,
    val active: Boolean = true,
)

data class UpdateShiftTemplateRequest(
    @field:NotBlank val name: String? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    @field:Min(0) @field:Max(240) val lateThresholdMinutes: Int? = null,
    val allowsOvertime: Boolean? = null,
    val active: Boolean? = null,
)

data class ShiftTemplateView(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val lateThresholdMinutes: Int,
    val allowsOvertime: Boolean,
    val active: Boolean,
    val overnight: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreateMemberShiftAssignmentRequest(
    @field:NotNull val shiftTemplateId: UUID?,
    @field:NotNull val effectiveFrom: LocalDate?,
    val effectiveTo: LocalDate? = null,
    val active: Boolean = true,
)

data class UpdateMemberShiftAssignmentRequest(
    val shiftTemplateId: UUID? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveTo: LocalDate? = null,
    val active: Boolean? = null,
)

data class MemberShiftAssignmentView(
    val id: UUID,
    val tenantId: UUID,
    val userId: UUID,
    val shiftTemplateId: UUID,
    val shiftName: String?,
    val shiftStartTime: LocalTime?,
    val shiftEndTime: LocalTime?,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
    val active: Boolean,
    val current: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun ShiftTemplate.isOvernight(): Boolean = !endTime.isAfter(startTime)

fun ShiftTemplate.toView(): ShiftTemplateView =
    ShiftTemplateView(
        id = id ?: error("shift id null"),
        tenantId = tenantId,
        name = name,
        startTime = startTime,
        endTime = endTime,
        lateThresholdMinutes = lateThresholdMinutes,
        allowsOvertime = allowsOvertime,
        active = active,
        overnight = isOvernight(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun MemberShiftAssignment.toView(shift: ShiftTemplate?, today: LocalDate): MemberShiftAssignmentView =
    MemberShiftAssignmentView(
        id = id ?: error("assignment id null"),
        tenantId = tenantId,
        userId = userId,
        shiftTemplateId = shiftTemplateId,
        shiftName = shift?.name,
        shiftStartTime = shift?.startTime,
        shiftEndTime = shift?.endTime,
        effectiveFrom = effectiveFrom,
        effectiveTo = effectiveTo,
        active = active,
        current = active && !effectiveFrom.isAfter(today) && (effectiveTo == null || !effectiveTo!!.isBefore(today)),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
