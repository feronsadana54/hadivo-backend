package com.hadivo.attendance.modules.auth

import com.hadivo.attendance.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<UserView>> {
        val user = auth.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(user))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<TokenPair> =
        ApiResponse.ok(auth.login(request))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ApiResponse<TokenPair> =
        ApiResponse.ok(auth.refresh(request.refreshToken))

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        auth.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
