package com.hadivo.attendance.modules.notification

interface NotificationGateway {
    val channel: NotificationChannel

    fun send(
        request: NotificationRequest,
        recipient: NotificationRecipient,
        template: NotificationTemplate,
        destination: String?,
    ): NotificationGatewayResult
}
