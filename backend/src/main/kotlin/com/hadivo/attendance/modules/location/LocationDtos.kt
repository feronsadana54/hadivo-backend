package com.hadivo.attendance.modules.location

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class CreateLocationRequest(
    @field:NotBlank val name: String,
    @field:Min(-90) @field:Max(90) val latitude: Double,
    @field:Min(-180) @field:Max(180) val longitude: Double,
    @field:Min(10) @field:Max(5000) val radiusMeters: Int,
)

data class UpdateLocationRequest(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
    val active: Boolean? = null,
)

data class LocationView(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val active: Boolean,
)
