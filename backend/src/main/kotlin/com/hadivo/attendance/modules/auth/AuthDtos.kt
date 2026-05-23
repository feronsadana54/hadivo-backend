package com.hadivo.attendance.modules.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100, message = "Password minimal 8 karakter.") val password: String,
    @field:NotBlank val fullName: String,
    val phone: String? = null,
)

data class LoginRequest(
    @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class LogoutRequest(
    @field:NotBlank val refreshToken: String,
)

data class TokenPair(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

data class UserView(
    val id: UUID,
    val email: String,
    val fullName: String,
    val phone: String?,
)
