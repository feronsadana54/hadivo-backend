package com.hadivo.attendance.modules.notification

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NotificationDeviceTokenService(
    private val tokens: NotificationDeviceTokenRepository,
) {

    @Transactional
    fun register(
        tenantId: UUID,
        userId: UUID,
        request: RegisterNotificationTokenRequest,
    ): NotificationDeviceTokenResponse {
        val now = Instant.now()
        val fcmToken = request.fcmToken.trim()
        val token = tokens.findByFcmToken(fcmToken)
            ?: NotificationDeviceToken(
                tenantId = tenantId,
                userId = userId,
                fcmToken = fcmToken,
            )

        token.tenantId = tenantId
        token.userId = userId
        token.deviceId = request.deviceId?.trim()?.takeIf { it.isNotBlank() }
        token.platform = request.platform?.trim()?.takeIf { it.isNotBlank() }
        token.active = true
        token.lastSeenAt = now

        return tokens.save(token).toResponse()
    }
}
