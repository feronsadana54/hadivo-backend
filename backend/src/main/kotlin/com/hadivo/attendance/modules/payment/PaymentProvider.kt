package com.hadivo.attendance.modules.payment

enum class PaymentProvider {
    MOCK,
    MIDTRANS;

    companion object {
        fun from(value: String?): PaymentProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MOCK
    }
}
