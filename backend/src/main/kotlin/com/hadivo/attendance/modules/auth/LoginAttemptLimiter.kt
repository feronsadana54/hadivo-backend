package com.hadivo.attendance.modules.auth

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class LoginAttemptLimiter {

    private val attempts = ConcurrentHashMap<String, LoginFailureState>()

    fun requireAllowed(email: String, now: Instant = Instant.now()) {
        val key = email.normalizedLoginKey()
        val state = attempts[key] ?: return
        if (state.lockedUntil?.isAfter(now) == true) {
            throw DomainException(ErrorCode.UNAUTHORIZED, LOCKOUT_MESSAGE)
        }
        if (state.lockedUntil != null || state.firstFailedAt.plus(WINDOW).isBefore(now)) {
            attempts.remove(key)
        }
    }

    fun recordFailure(email: String, now: Instant = Instant.now()) {
        val key = email.normalizedLoginKey()
        attempts.compute(key) { _, current ->
            val state = if (current == null || current.firstFailedAt.plus(WINDOW).isBefore(now)) {
                LoginFailureState(failureCount = 0, firstFailedAt = now)
            } else {
                current
            }
            val nextCount = state.failureCount + 1
            state.copy(
                failureCount = nextCount,
                lockedUntil = if (nextCount >= MAX_FAILURES) now.plus(LOCK_DURATION) else state.lockedUntil,
            )
        }
    }

    fun reset(email: String) {
        attempts.remove(email.normalizedLoginKey())
    }

    private fun String.normalizedLoginKey(): String = trim().lowercase()

    private data class LoginFailureState(
        val failureCount: Int,
        val firstFailedAt: Instant,
        val lockedUntil: Instant? = null,
    )

    companion object {
        const val LOCKOUT_MESSAGE = "Terlalu banyak percobaan login gagal. Coba lagi beberapa menit lagi."
        private const val MAX_FAILURES = 5
        private val WINDOW: Duration = Duration.ofMinutes(15)
        private val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
