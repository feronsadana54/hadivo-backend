package com.hadivo.attendance.modules.notification

object NotificationDestinationMasker {
    fun email(value: String?): String? {
        val email = value?.trim().orEmpty()
        if (email.isBlank()) return null
        val at = email.indexOf('@')
        if (at <= 0) return maskToken(email)
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        val visible = local.take(1)
        return "$visible***@$domain"
    }

    fun pushTokens(tokens: List<String>): String? {
        if (tokens.isEmpty()) return null
        val first = maskToken(tokens.first())
        return if (tokens.size == 1) "fcm:$first" else "fcm:$first (+${tokens.size - 1} more)"
    }

    fun token(value: String?): String? {
        val token = value?.trim().orEmpty()
        if (token.isBlank()) return null
        return maskToken(token)
    }

    private fun maskToken(value: String): String {
        val clean = value.trim()
        if (clean.length <= 10) return "***"
        return "${clean.take(6)}...${clean.takeLast(4)}"
    }
}
