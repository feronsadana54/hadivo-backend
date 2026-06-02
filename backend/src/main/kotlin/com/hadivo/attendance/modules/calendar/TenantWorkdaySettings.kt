package com.hadivo.attendance.modules.calendar

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tenant_workday_settings")
class TenantWorkdaySettings(
    @Column(name = "tenant_id", nullable = false, unique = true)
    var tenantId: UUID,

    @Column(name = "monday_workday", nullable = false)
    var mondayWorkday: Boolean = true,

    @Column(name = "tuesday_workday", nullable = false)
    var tuesdayWorkday: Boolean = true,

    @Column(name = "wednesday_workday", nullable = false)
    var wednesdayWorkday: Boolean = true,

    @Column(name = "thursday_workday", nullable = false)
    var thursdayWorkday: Boolean = true,

    @Column(name = "friday_workday", nullable = false)
    var fridayWorkday: Boolean = true,

    @Column(name = "saturday_workday", nullable = false)
    var saturdayWorkday: Boolean = false,

    @Column(name = "sunday_workday", nullable = false)
    var sundayWorkday: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touchUpdatedAt() {
        updatedAt = Instant.now()
    }

    fun isWorkday(day: DayOfWeek): Boolean = when (day) {
        DayOfWeek.MONDAY -> mondayWorkday
        DayOfWeek.TUESDAY -> tuesdayWorkday
        DayOfWeek.WEDNESDAY -> wednesdayWorkday
        DayOfWeek.THURSDAY -> thursdayWorkday
        DayOfWeek.FRIDAY -> fridayWorkday
        DayOfWeek.SATURDAY -> saturdayWorkday
        DayOfWeek.SUNDAY -> sundayWorkday
    }

    fun hasAnyWorkday(): Boolean =
        mondayWorkday || tuesdayWorkday || wednesdayWorkday || thursdayWorkday ||
            fridayWorkday || saturdayWorkday || sundayWorkday
}
