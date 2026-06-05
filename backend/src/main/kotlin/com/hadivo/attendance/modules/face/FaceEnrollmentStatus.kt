package com.hadivo.attendance.modules.face

enum class FaceEnrollmentStatus {
    PENDING,
    ACTIVE,
    RESET;

    companion object {
        fun parse(raw: String?): FaceEnrollmentStatus? =
            raw?.trim()?.uppercase()?.let { name -> values().firstOrNull { it.name == name } }
    }
}
