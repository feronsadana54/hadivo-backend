package com.hadivo.attendance.modules.face

import java.util.UUID

interface FaceVerifier {
    fun verify(userId: UUID, imageBase64: String?): Boolean
}
