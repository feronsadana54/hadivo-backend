package com.hadivo.attendance.modules.settings

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantAttendanceSettingsRepository : JpaRepository<TenantAttendanceSettings, UUID>
