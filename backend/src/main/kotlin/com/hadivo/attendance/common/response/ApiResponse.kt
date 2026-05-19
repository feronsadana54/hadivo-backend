package com.hadivo.attendance.common.response

data class ApiResponse<T>(
    val data: T? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(data = data)
        fun fail(error: ApiError): ApiResponse<Nothing> = ApiResponse(error = error)
    }
}

data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null,
)
