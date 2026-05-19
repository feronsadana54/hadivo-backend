package com.hadivo.attendance.common.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object TimeUtils {

    fun zone(timezone: String): ZoneId = ZoneId.of(timezone)

    fun nowAt(timezone: String): ZonedDateTime = ZonedDateTime.now(zone(timezone))

    fun todayAt(timezone: String): LocalDate = nowAt(timezone).toLocalDate()

    fun durationMinutes(start: ZonedDateTime, end: ZonedDateTime): Int =
        Duration.between(start, end).toMinutes().toInt()

    fun isAfterWithGrace(now: LocalTime, threshold: LocalTime, graceMinutes: Int): Boolean =
        now.isAfter(threshold.plusMinutes(graceMinutes.toLong()))
}
