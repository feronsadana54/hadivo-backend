package com.hadivo.attendance.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = header.removePrefix("Bearer ").trim()
        try {
            val claims = jwtService.parse(token)
            val userId = UUID.fromString(claims.subject)
            val email = claims["email"] as String
            val principal = AuthPrincipal(userId = userId, email = email)
            val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
        } catch (ex: Exception) {
            log.debug("JWT validation failed: {}", ex.message)
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}
