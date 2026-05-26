package com.hadivo.attendance.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hadivo")
data class AppProperties(
    val jwt: Jwt,
    val messaging: Messaging,
    val notification: Notification,
    val payment: Payment,
    val seed: Seed,
) {
    data class Jwt(
        val secret: String,
        val accessTtlMinutes: Long,
        val refreshTtlDays: Long,
        val issuer: String,
    )

    data class Messaging(
        val exchange: String,
        val routingKeys: RoutingKeys,
        val notificationQueue: String,
        val notificationEventsQueue: String,
        val notificationRoutingKey: String,
    ) {
        data class RoutingKeys(
            val clockIn: String,
            val clockOut: String,
            val attemptFailed: String,
        )
    }

    data class Notification(
        val email: Email,
        val push: Push,
    ) {
        data class Email(
            val provider: String,
            val resend: Resend,
        ) {
            data class Resend(
                val apiKey: String,
                val from: String,
            )
        }

        data class Push(
            val provider: String,
            val fcm: Fcm,
        ) {
            data class Fcm(
                val enabled: Boolean,
                val projectId: String,
                val serviceAccountPath: String,
            )
        }
    }

    data class Payment(
        val provider: String,
        val midtrans: Midtrans,
    ) {
        data class Midtrans(
            val enabled: Boolean,
            val environment: String,
            val serverKey: String,
            val clientKey: String,
            val snapBaseUrl: String,
            val apiBaseUrl: String,
        )
    }

    data class Seed(
        val superAdminEmail: String,
        val superAdminPassword: String,
    )
}
