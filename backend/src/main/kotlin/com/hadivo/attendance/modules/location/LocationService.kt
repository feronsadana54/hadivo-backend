package com.hadivo.attendance.modules.location

import com.hadivo.attendance.common.exception.DomainException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LocationService(private val locations: TenantLocationRepository) {

    @Transactional
    fun create(tenantId: UUID, request: CreateLocationRequest): TenantLocation =
        locations.save(
            TenantLocation(
                tenantId = tenantId,
                name = request.name,
                latitude = request.latitude,
                longitude = request.longitude,
                radiusMeters = request.radiusMeters,
            )
        )

    fun list(tenantId: UUID): List<TenantLocation> = locations.findAllByTenantId(tenantId)

    fun activeFor(tenantId: UUID): List<TenantLocation> =
        locations.findAllByTenantIdAndActive(tenantId, true)

    @Transactional
    fun update(tenantId: UUID, locationId: UUID, request: UpdateLocationRequest): TenantLocation {
        val location = locations.findById(locationId)
            .orElseThrow { DomainException.notFound("Location", locationId) }
        if (location.tenantId != tenantId) {
            throw DomainException.notFound("Location", locationId)
        }
        request.name?.let { location.name = it }
        request.latitude?.let { location.latitude = it }
        request.longitude?.let { location.longitude = it }
        request.radiusMeters?.let { location.radiusMeters = it }
        request.active?.let { location.active = it }
        return locations.save(location)
    }
}

fun TenantLocation.toView(): LocationView = LocationView(
    id = id ?: error("Location belum tersimpan"),
    tenantId = tenantId,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    active = active,
)
