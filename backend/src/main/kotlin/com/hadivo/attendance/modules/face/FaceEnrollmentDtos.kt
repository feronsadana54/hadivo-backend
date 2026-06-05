package com.hadivo.attendance.modules.face

import java.time.Instant
import java.util.UUID

data class EnrollFaceRequest(
    val imageBase64: String?,
    val consentGiven: Boolean? = null,
)

/**
 * Public-facing view. Intentionally omits `imageReference` and `embeddingReference`
 * so we never leak storage paths to frontend / mobile / audit consumers.
 */
data class FaceProfileView(
    val profileId: UUID,
    val enrollmentStatus: FaceEnrollmentStatus,
    val consentGiven: Boolean,
    val imageStored: Boolean,
    val enrolledAt: Instant?,
    val resetAt: Instant?,
    val updatedAt: Instant,
    val message: String,
)
