package com.hadivo.attendance.modules.auth

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class PasswordPolicy {

    fun validate(rawPassword: String) {
        if (
            rawPassword.length < 8 ||
            rawPassword.none { it.isLetter() } ||
            rawPassword.none { it.isDigit() }
        ) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Password minimal 8 karakter dan harus memiliki huruf serta angka.",
            )
        }
    }
}
