package com.hadivo.attendance.common.exception

class DomainException(
    val code: ErrorCode,
    message: String? = null,
    val details: Map<String, Any?> = emptyMap(),
) : RuntimeException(message ?: code.defaultMessage) {

    companion object {
        fun notFound(resource: String, id: Any? = null): DomainException =
            DomainException(
                ErrorCode.NOT_FOUND,
                "$resource tidak ditemukan",
                if (id != null) mapOf("id" to id) else emptyMap(),
            )

        fun forbidden(reason: String? = null): DomainException =
            DomainException(ErrorCode.FORBIDDEN, reason)

        fun conflict(reason: String): DomainException =
            DomainException(ErrorCode.CONFLICT, reason)
    }
}
