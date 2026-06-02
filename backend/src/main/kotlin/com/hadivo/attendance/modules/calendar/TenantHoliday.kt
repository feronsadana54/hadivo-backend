package com.hadivo.attendance.modules.calendar

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "tenant_holidays")
class TenantHoliday(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "holiday_date", nullable = false)
    var holidayDate: LocalDate,

    @Column(nullable = false, length = 120)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: HolidayType = HolidayType.CUSTOM,

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
}
