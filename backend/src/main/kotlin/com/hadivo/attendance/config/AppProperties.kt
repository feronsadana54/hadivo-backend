package com.hadivo.attendance.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hadivo")
data class AppProperties(
    val jwt: Jwt,
    val messaging: Messaging,
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

    data class Seed(
        val superAdminEmail: String,
        val superAdminPassword: String,
    )
}
