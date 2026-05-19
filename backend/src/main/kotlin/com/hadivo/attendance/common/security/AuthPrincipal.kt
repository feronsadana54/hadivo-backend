package com.hadivo.attendance.common.security

import java.util.UUID

data class AuthPrincipal(
    val userId: UUID,
    val email: String,
)
