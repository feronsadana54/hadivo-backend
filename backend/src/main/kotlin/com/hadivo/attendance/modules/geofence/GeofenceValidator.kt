package com.hadivo.attendance.modules.geofence

import com.hadivo.attendance.common.util.GeoUtils
import com.hadivo.attendance.modules.location.TenantLocation
import org.springframework.stereotype.Component

@Component
class GeofenceValidator {

    fun findMatching(latitude: Double, longitude: Double, locations: List<TenantLocation>): TenantLocation? =
        locations.firstOrNull { it.active && GeoUtils.isWithinRadius(latitude, longitude, it.latitude, it.longitude, it.radiusMeters) }

    fun isInside(latitude: Double, longitude: Double, location: TenantLocation): Boolean =
        GeoUtils.isWithinRadius(latitude, longitude, location.latitude, location.longitude, location.radiusMeters)
}
