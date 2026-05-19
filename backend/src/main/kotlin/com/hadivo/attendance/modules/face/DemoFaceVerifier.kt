package com.hadivo.attendance.modules.face

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Placeholder verifier untuk Fase 1. Mengembalikan true bila base64 dikirim
 * dan panjangnya wajar — cukup untuk demo end-to-end flow. Implementasi
 * embedding/ML yang sebenarnya akan menggantikan bean ini di Fase 2.
 */
@Component
class DemoFaceVerifier : FaceVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun verify(userId: UUID, imageBase64: String?): Boolean {
        if (imageBase64.isNullOrBlank()) return false
        if (imageBase64.length < MIN_PAYLOAD_LENGTH) return false
        log.debug("Face check passed (demo) for user={}", userId)
        return true
    }

    private companion object {
        const val MIN_PAYLOAD_LENGTH = 32
    }
}
