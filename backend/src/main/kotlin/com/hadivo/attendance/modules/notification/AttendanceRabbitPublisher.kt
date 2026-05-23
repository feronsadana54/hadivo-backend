package com.hadivo.attendance.modules.notification

import com.hadivo.attendance.modules.attendance.AttemptFailed
import com.hadivo.attendance.modules.attendance.AttemptReason
import com.hadivo.attendance.modules.attendance.ClockInOccurred
import com.hadivo.attendance.modules.attendance.ClockOutOccurred
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AttendanceRabbitPublisher(
    private val notifications: NotificationPublisher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onClockIn(event: ClockInOccurred) {
        notifications.publish(
            NotificationRequest(
                eventType = NotificationEventType.CLOCK_IN_SUCCESS,
                tenantId = event.tenantId,
                actorUserId = event.userId,
                occurredAt = event.occurredAt,
                metadata = mapOf("recordId" to event.recordId, "status" to event.status.name),
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onClockOut(event: ClockOutOccurred) {
        notifications.publish(
            NotificationRequest(
                eventType = NotificationEventType.CLOCK_OUT_SUCCESS,
                tenantId = event.tenantId,
                actorUserId = event.userId,
                occurredAt = event.occurredAt,
                metadata = mapOf(
                    "recordId" to event.recordId,
                    "status" to event.status.name,
                    "workDurationMinutes" to event.workDurationMinutes,
                ),
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAttemptFailed(event: AttemptFailed) {
        notifications.publish(
            NotificationRequest(
                eventType = event.reason.toNotificationEventType(),
                tenantId = event.tenantId,
                actorUserId = event.userId,
                occurredAt = event.occurredAt,
                metadata = mapOf("attemptType" to event.type.name, "reason" to event.reason.name),
            )
        )
    }

    private fun AttemptReason.toNotificationEventType(): NotificationEventType =
        when (this) {
            AttemptReason.OUT_OF_RADIUS -> NotificationEventType.ATTENDANCE_OUT_OF_RADIUS
            AttemptReason.DEVICE_MISMATCH -> NotificationEventType.DEVICE_MISMATCH
            else -> NotificationEventType.ATTENDANCE_FAILED_ATTEMPT
        }
}
