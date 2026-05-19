package com.hadivo.attendance.modules.attendance

enum class AttemptReason {
    OUT_OF_RADIUS,
    FACE_MISMATCH,
    INVALID_LOCATION,
    DEVICE_MISMATCH,
    DUPLICATE_CLOCK_IN,
    NO_CLOCK_IN,
    ALREADY_CLOCKED_OUT,
    LATE_NOT_ALLOWED,
}
