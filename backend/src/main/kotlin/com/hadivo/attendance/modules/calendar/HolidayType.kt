package com.hadivo.attendance.modules.calendar

enum class HolidayType {
    CUSTOM,
    NATIONAL,
    COMPANY,
    SCHOOL;

    companion object {
        fun parse(raw: String?): HolidayType? = raw?.uppercase()?.let { value ->
            entries.firstOrNull { it.name == value }
        }
    }
}
