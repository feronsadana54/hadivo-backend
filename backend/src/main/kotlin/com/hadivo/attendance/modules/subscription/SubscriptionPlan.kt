package com.hadivo.attendance.modules.subscription

enum class SubscriptionPlan(val maxMembers: Int) {
    FREE(10),
    PRO(100),
    BUSINESS(500),
    ENTERPRISE(-1);

    fun isUnlimited(): Boolean = maxMembers < 0
}
