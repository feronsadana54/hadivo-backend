package com.hadivo.attendance.modules.leave

enum class LeaveRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    fun isTerminal(): Boolean = this != PENDING
}
