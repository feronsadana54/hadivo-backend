package com.hadivo.attendance.modules.notification

import com.hadivo.attendance.config.AppProperties
import com.hadivo.attendance.modules.attendance.AttemptFailed
import com.hadivo.attendance.modules.attendance.ClockInOccurred
import com.hadivo.attendance.modules.attendance.ClockOutOccurred
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AttendanceRabbitPublisher(
    private val rabbit: RabbitTemplate,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onClockIn(event: ClockInOccurred) {
        val message = NotificationMessage(
            type = "ATTENDANCE_CLOCK_IN",
            tenantId = event.tenantId,
            userId = event.userId,
            occurredAt = event.occurredAt,
            data = mapOf("recordId" to event.recordId, "status" to event.status.name),
        )
        send(props.messaging.routingKeys.clockIn, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onClockOut(event: ClockOutOccurred) {
        val message = NotificationMessage(
            type = "ATTENDANCE_CLOCK_OUT",
            tenantId = event.tenantId,
            userId = event.userId,
            occurredAt = event.occurredAt,
            data = mapOf(
                "recordId" to event.recordId,
                "status" to event.status.name,
                "workDurationMinutes" to event.workDurationMinutes,
            ),
        )
        send(props.messaging.routingKeys.clockOut, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAttemptFailed(event: AttemptFailed) {
        val message = NotificationMessage(
            type = "ATTENDANCE_ATTEMPT_FAILED",
            tenantId = event.tenantId,
            userId = event.userId,
            occurredAt = event.occurredAt,
            data = mapOf("attemptType" to event.type.name, "reason" to event.reason.name),
        )
        send(props.messaging.routingKeys.attemptFailed, message)
    }

    private fun send(routingKey: String, message: NotificationMessage) {
        try {
            rabbit.convertAndSend(props.messaging.exchange, routingKey, message)
        } catch (ex: Exception) {
            log.warn("Failed to publish {} to RabbitMQ: {}", routingKey, ex.message)
        }
    }
}
