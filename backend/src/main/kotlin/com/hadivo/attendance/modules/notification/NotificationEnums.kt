package com.hadivo.attendance.modules.notification

enum class NotificationEventType {
    CLOCK_IN_SUCCESS,
    CLOCK_OUT_SUCCESS,
    ATTENDANCE_OUT_OF_RADIUS,
    DEVICE_MISMATCH,
    ATTENDANCE_FAILED_ATTEMPT,
    LEAVE_REQUEST_CREATED,
    LEAVE_REQUEST_APPROVED,
    LEAVE_REQUEST_REJECTED,
    LEAVE_REQUEST_CANCELLED,
}

enum class NotificationChannel {
    IN_APP,
    EMAIL,
    PUSH,
}

enum class NotificationDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED,
}
