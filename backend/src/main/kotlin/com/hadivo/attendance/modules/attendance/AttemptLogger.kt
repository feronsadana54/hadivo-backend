package com.hadivo.attendance.modules.attendance

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class AttemptLogger(
    private val attempts: AttendanceAttemptRepository,
    private val publisher: ApplicationEventPublisher,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun log(
        tenantId: UUID,
        userId: UUID,
        type: AttendanceType,
        reason: AttemptReason,
        latitude: Double?,
        longitude: Double?,
        deviceId: String?,
    ) {
        attempts.save(
            AttendanceAttempt(
                tenantId = tenantId,
                userId = userId,
                type = type,
                reason = reason,
                latitude = latitude,
                longitude = longitude,
                deviceId = deviceId,
            )
        )
        publisher.publishEvent(
            AttemptFailed(
                tenantId = tenantId,
                userId = userId,
                type = type,
                reason = reason,
                occurredAt = Instant.now(),
            )
        )
    }
}
