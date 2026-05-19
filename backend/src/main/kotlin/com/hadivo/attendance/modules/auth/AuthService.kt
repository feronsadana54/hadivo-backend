package com.hadivo.attendance.modules.auth

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.security.JwtService
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
) {

    @Transactional
    fun register(request: RegisterRequest): UserView {
        if (users.existsByEmail(request.email)) {
            throw DomainException.conflict("Email sudah terdaftar")
        }
        val user = User(
            email = request.email.lowercase(),
            passwordHash = passwordEncoder.encode(request.password),
            fullName = request.fullName,
            phone = request.phone,
        )
        users.save(user)
        return user.toView()
    }

    @Transactional
    fun login(request: LoginRequest): TokenPair {
        val user = users.findByEmail(request.email.lowercase())
            ?: throw DomainException(ErrorCode.UNAUTHORIZED, "Email atau password salah")
        if (!user.active) {
            throw DomainException(ErrorCode.FORBIDDEN, "Akun dinonaktifkan")
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw DomainException(ErrorCode.UNAUTHORIZED, "Email atau password salah")
        }
        return issueTokensFor(user)
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

        return issueTokensFor(user)
    }

    @Transactional
    fun logout(rawToken: String) {
        val hash = jwt.hash(rawToken)
        val stored = refreshTokens.findByTokenHash(hash) ?: return
        if (stored.revokedAt == null) {
            stored.revokedAt = Instant.now()
            refreshTokens.save(stored)
        }
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
}
