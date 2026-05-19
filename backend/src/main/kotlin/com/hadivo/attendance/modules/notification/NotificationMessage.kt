package com.hadivo.attendance.modules.notification

import java.time.Instant
import java.util.UUID

data class NotificationMessage(
    val type: String,
    val tenantId: UUID,
    val userId: UUID,
    val occurredAt: Instant,
    val data: Map<String, Any?> = emptyMap(),
)
