package com.hadivo.attendance.modules.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findAllByRecipientUserIdOrderByCreatedAtDesc(recipientUserId: UUID): List<Notification>
}
