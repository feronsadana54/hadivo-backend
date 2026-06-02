package com.hadivo.attendance.modules.calendar

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UpdateWorkdaySettingsRequest(
    val mondayWorkday: Boolean? = null,
    val tuesdayWorkday: Boolean? = null,
    val wednesdayWorkday: Boolean? = null,
    val thursdayWorkday: Boolean? = null,
    val fridayWorkday: Boolean? = null,
    val saturdayWorkday: Boolean? = null,
    val sundayWorkday: Boolean? = null,
    val active: Boolean? = null,
)

data class WorkdaySettingsView(
    val id: UUID,
    val tenantId: UUID,
    val mondayWorkday: Boolean,
    val tuesdayWorkday: Boolean,
    val wednesdayWorkday: Boolean,
    val thursdayWorkday: Boolean,
    val fridayWorkday: Boolean,
    val saturdayWorkday: Boolean,
    val sundayWorkday: Boolean,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreateHolidayRequest(
    val holidayDate: LocalDate?,
    val name: String?,
    val type: String? = null,
    val active: Boolean? = null,
)

data class UpdateHolidayRequest(
    val holidayDate: LocalDate? = null,
    val name: String? = null,
    val type: String? = null,
    val active: Boolean? = null,
)

data class HolidayView(
    val id: UUID,
    val tenantId: UUID,
    val holidayDate: LocalDate,
    val name: String,
    val type: HolidayType,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
