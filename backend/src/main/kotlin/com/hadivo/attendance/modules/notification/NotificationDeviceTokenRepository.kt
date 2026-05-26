package com.hadivo.attendance.modules.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationDeviceTokenRepository : JpaRepository<NotificationDeviceToken, UUID> {
    fun findByFcmToken(fcmToken: String): NotificationDeviceToken?

    fun findAllByTenantIdAndUserIdInAndActiveTrue(
        tenantId: UUID,
        userIds: Collection<UUID>,
    ): List<NotificationDeviceToken>
}
