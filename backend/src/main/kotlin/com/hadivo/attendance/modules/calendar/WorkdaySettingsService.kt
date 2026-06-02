package com.hadivo.attendance.modules.calendar

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkdaySettingsService(
    private val repository: TenantWorkdaySettingsRepository,
) {

    @Transactional
    fun getOrCreateDefault(tenantId: UUID): TenantWorkdaySettings {
        repository.findByTenantId(tenantId)?.let { return it }
        return repository.save(TenantWorkdaySettings(tenantId = tenantId))
    }

    @Transactional
    fun update(
        tenantId: UUID,
        request: UpdateWorkdaySettingsRequest,
    ): Pair<TenantWorkdaySettings, List<String>> {
        val settings = getOrCreateDefault(tenantId)
        val changed = mutableListOf<String>()

        request.mondayWorkday?.let {
            if (settings.mondayWorkday != it) { settings.mondayWorkday = it; changed.add("mondayWorkday") }
        }
        request.tuesdayWorkday?.let {
            if (settings.tuesdayWorkday != it) { settings.tuesdayWorkday = it; changed.add("tuesdayWorkday") }
        }
        request.wednesdayWorkday?.let {
            if (settings.wednesdayWorkday != it) { settings.wednesdayWorkday = it; changed.add("wednesdayWorkday") }
        }
        request.thursdayWorkday?.let {
            if (settings.thursdayWorkday != it) { settings.thursdayWorkday = it; changed.add("thursdayWorkday") }
        }
        request.fridayWorkday?.let {
            if (settings.fridayWorkday != it) { settings.fridayWorkday = it; changed.add("fridayWorkday") }
        }
        request.saturdayWorkday?.let {
            if (settings.saturdayWorkday != it) { settings.saturdayWorkday = it; changed.add("saturdayWorkday") }
        }
        request.sundayWorkday?.let {
            if (settings.sundayWorkday != it) { settings.sundayWorkday = it; changed.add("sundayWorkday") }
        }
        request.active?.let {
            if (settings.active != it) { settings.active = it; changed.add("active") }
        }

        if (!settings.hasAnyWorkday()) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Minimal satu hari dalam seminggu harus menjadi hari kerja",
            )
        }

        val saved = repository.save(settings)
        return saved to changed
    }
}

fun TenantWorkdaySettings.toView(): WorkdaySettingsView = WorkdaySettingsView(
    id = id ?: error("workday settings id null"),
    tenantId = tenantId,
    mondayWorkday = mondayWorkday,
    tuesdayWorkday = tuesdayWorkday,
    wednesdayWorkday = wednesdayWorkday,
    thursdayWorkday = thursdayWorkday,
    fridayWorkday = fridayWorkday,
    saturdayWorkday = saturdayWorkday,
    sundayWorkday = sundayWorkday,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
