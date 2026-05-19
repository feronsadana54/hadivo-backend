package com.hadivo.attendance.common.security

import com.hadivo.attendance.config.AppProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(private val props: AppProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.jwt.secret.toByteArray(Charsets.UTF_8))

    fun issueAccessToken(userId: UUID, email: String): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plus(props.jwt.accessTtlMinutes, ChronoUnit.MINUTES)
        val token = Jwts.builder()
            .subject(userId.toString())
            .issuer(props.jwt.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("email", email)
            .signWith(key)
            .compact()
        return IssuedToken(token, expiresAt)
    }

    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    fun issueRefreshToken(): RefreshTokenPair {
        val raw = "${UUID.randomUUID()}.${UUID.randomUUID()}"
        val expiresAt = Instant.now().plus(props.jwt.refreshTtlDays, ChronoUnit.DAYS)
        return RefreshTokenPair(raw = raw, hash = hash(raw), expiresAt = expiresAt)
    }

    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    data class IssuedToken(val token: String, val expiresAt: Instant)
    data class RefreshTokenPair(val raw: String, val hash: String, val expiresAt: Instant)
}
