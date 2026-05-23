package com.hadivo.attendance.modules.auth

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.security.JwtService
import com.hadivo.attendance.modules.audit.AuditLogger
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val jwt: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val loginAttempts: LoginAttemptLimiter,
    private val passwordPolicy: PasswordPolicy,
    private val audit: AuditLogger,
) {

    @Transactional
    fun register(request: RegisterRequest): UserView {
        passwordPolicy.validate(request.password)
        val email = request.email.lowercase()
        if (users.existsByEmail(email)) {
            throw DomainException.conflict("Email sudah terdaftar")
        }
        val user = User(
            email = email,
            passwordHash = passwordEncoder.encode(request.password),
            fullName = request.fullName,
            phone = request.phone,
        )
        users.save(user)
        return user.toView()
    }

    @Transactional
    fun login(request: LoginRequest): TokenPair {
        val email = request.email.lowercase()
        try {
            loginAttempts.requireAllowed(email)
        } catch (ex: DomainException) {
            audit.log(
                tenantId = null,
                actorUserId = null,
                action = "LOGIN_FAILED",
                resourceType = "Auth",
                metadata = mapOf(
                    "email" to maskEmail(email),
                    "reason" to "LOCKED",
                    "attemptedAt" to Instant.now().toString(),
                ),
            )
            throw ex
        }

        val user = users.findByEmail(email)
        if (user == null || !user.active || !passwordEncoder.matches(request.password, user.passwordHash)) {
            loginAttempts.recordFailure(email)
            audit.log(
                tenantId = null,
                actorUserId = user?.id,
                action = "LOGIN_FAILED",
                resourceType = "Auth",
                metadata = mapOf(
                    "email" to maskEmail(email),
                    "reason" to "INVALID_CREDENTIALS",
                    "attemptedAt" to Instant.now().toString(),
                ),
            )
            throw DomainException(ErrorCode.UNAUTHORIZED, LOGIN_ERROR_MESSAGE)
        }

        loginAttempts.reset(email)
        val tokens = issueTokensFor(user)
        audit.log(
            tenantId = null,
            actorUserId = user.id,
            action = "LOGIN_SUCCESS",
            resourceType = "Auth",
            metadata = mapOf("email" to maskEmail(email)),
        )
        return tokens
    }

    @Transactional
    fun refresh(rawToken: String): TokenPair {
        val hash = jwt.hash(rawToken)
        val stored = refreshTokens.findByTokenHash(hash)
            ?: throw DomainException(ErrorCode.UNAUTHORIZED, "Refresh token tidak dikenal")
        if (!stored.isUsable()) {
            throw DomainException(ErrorCode.UNAUTHORIZED, "Refresh token sudah tidak berlaku")
        }
        val user = users.findById(stored.userId)
            .orElseThrow { DomainException(ErrorCode.UNAUTHORIZED, "User tidak ditemukan") }

        stored.revokedAt = Instant.now()
        refreshTokens.save(stored)

        val tokens = issueTokensFor(user)
        audit.log(
            tenantId = null,
            actorUserId = user.id,
            action = "REFRESH_TOKEN_ROTATED",
            resourceType = "RefreshToken",
            resourceId = stored.id?.toString(),
        )
        return tokens
    }

    @Transactional
    fun logout(rawToken: String) {
        val hash = jwt.hash(rawToken)
        val stored = refreshTokens.findByTokenHash(hash) ?: return
        if (stored.revokedAt == null) {
            stored.revokedAt = Instant.now()
            refreshTokens.save(stored)
        }
        audit.log(
            tenantId = null,
            actorUserId = stored.userId,
            action = "LOGOUT",
            resourceType = "RefreshToken",
            resourceId = stored.id?.toString(),
        )
    }

    private fun issueTokensFor(user: User): TokenPair {
        val userId = user.id ?: error("User belum tersimpan")
        val access = jwt.issueAccessToken(userId, user.email)
        val refresh = jwt.issueRefreshToken()
        refreshTokens.save(
            RefreshToken(
                userId = userId,
                tokenHash = refresh.hash,
                expiresAt = refresh.expiresAt,
            )
        )
        return TokenPair(
            accessToken = access.token,
            accessTokenExpiresAt = access.expiresAt,
            refreshToken = refresh.raw,
            refreshTokenExpiresAt = refresh.expiresAt,
        )
    }

    private fun User.toView(): UserView = UserView(
        id = id ?: error("id null"),
        email = email,
        fullName = fullName,
        phone = phone,
    )

    private fun maskEmail(email: String): String {
        val normalized = email.lowercase()
        val atIndex = normalized.indexOf('@')
        if (atIndex <= 0) return "***"
        val local = normalized.substring(0, atIndex)
        val domain = normalized.substring(atIndex)
        val visible = local.take(2)
        return "$visible***$domain"
    }

    companion object {
        const val LOGIN_ERROR_MESSAGE = "Email atau password tidak sesuai."
    }
}
