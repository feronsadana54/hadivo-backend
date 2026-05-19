package com.hadivo.attendance.modules.tenant

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class CreateTenantRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Pattern(regexp = "^[a-z0-9-]{3,60}$") val slug: String,
    val mode: TenantMode,
    val timezone: String = "Asia/Jakarta",
)

data class UpdateTenantRequest(
    val name: String? = null,
    val timezone: String? = null,
    val active: Boolean? = null,
)

data class TenantView(
    val id: UUID,
    val name: String,
    val slug: String,
    val mode: TenantMode,
    val timezone: String,
    val active: Boolean,
)
