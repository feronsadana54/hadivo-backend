package com.hadivo.attendance.common.response

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> of(page: Page<T>): PageResponse<T> = PageResponse(
            items = page.content,
            page = page.number,
            size = page.size,
            totalItems = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}
