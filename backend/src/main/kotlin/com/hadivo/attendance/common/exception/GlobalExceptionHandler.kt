package com.hadivo.attendance.common.exception

import com.hadivo.attendance.common.response.ApiError
import com.hadivo.attendance.common.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException): ResponseEntity<ApiResponse<Nothing>> {
        val body = ApiResponse.fail(
            ApiError(
                code = ex.code.name,
                message = ex.message ?: ex.code.defaultMessage,
                details = ex.details.takeIf { it.isNotEmpty() },
            ),
        )
        return ResponseEntity.status(ex.code.status).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val fields = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        val body = ApiResponse.fail(
            ApiError(
                code = ErrorCode.VALIDATION_FAILED.name,
                message = ErrorCode.VALIDATION_FAILED.defaultMessage,
                details = mapOf("fields" to fields),
            ),
        )
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status).body(body)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ApiResponse<Nothing>> {
        val body = ApiResponse.fail(
            ApiError(ErrorCode.UNAUTHORIZED.name, ex.message ?: ErrorCode.UNAUTHORIZED.defaultMessage),
        )
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status).body(body)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        val body = ApiResponse.fail(
            ApiError(ErrorCode.FORBIDDEN.name, ex.message ?: ErrorCode.FORBIDDEN.defaultMessage),
        )
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception", ex)
        val body = ApiResponse.fail(
            ApiError(ErrorCode.INTERNAL.name, ErrorCode.INTERNAL.defaultMessage),
        )
        return ResponseEntity.status(ErrorCode.INTERNAL.status).body(body)
    }
}
