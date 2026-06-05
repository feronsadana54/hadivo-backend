package com.hadivo.attendance.modules.face

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.membership.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * v1.6.0 Face Enrollment Foundation.
 *
 * Scope:
 *  - Persist a per-user face profile with explicit consent.
 *  - Save the enrollment image to local storage and remember a relative reference.
 *  - Allow admin to reset the profile (clears references + best-effort file delete).
 *
 * Out of scope (NOT implemented):
 *  - Real face detection / matching.
 *  - Embedding extraction.
 *  - Liveness or anti-spoofing.
 *  - Gating clock-in / clock-out on enrollment.
 */
@Service
class FaceEnrollmentService(
    private val repository: UserFaceProfileRepository,
    private val memberships: MembershipRepository,
    private val storage: FaceImageStorage,
) {

    @Transactional(readOnly = true)
    fun getOrEmpty(tenantId: UUID, userId: UUID): FaceProfileView {
        requireMember(tenantId, userId)
        val profile = repository.findByTenantIdAndUserId(tenantId, userId)
        return profile?.toView(message = profile.statusMessage())
            ?: emptyView()
    }

    @Transactional
    fun enroll(tenantId: UUID, userId: UUID, request: EnrollFaceRequest): FaceProfileView {
        requireMember(tenantId, userId)
        val consent = request.consentGiven == true
        if (!consent) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Persetujuan penggunaan data wajah wajib diberikan",
            )
        }

        val stored = storage.store(tenantId, userId, request.imageBase64)
        val now = Instant.now()
        val existing = repository.findByTenantIdAndUserId(tenantId, userId)
        val profile = if (existing != null) {
            // Re-enrollment: drop the previous image file before overwriting the reference.
            val previousReference = existing.imageReference
            existing.consentGiven = true
            existing.consentGivenAt = now
            existing.imageReference = stored.reference
            existing.embeddingReference = null
            existing.enrolledAt = now
            existing.resetAt = null
            existing.enrollmentStatus = FaceEnrollmentStatus.ACTIVE
            val saved = repository.save(existing)
            if (previousReference != null && previousReference != stored.reference) {
                storage.delete(previousReference)
            }
            saved
        } else {
            repository.save(
                UserFaceProfile(
                    tenantId = tenantId,
                    userId = userId,
                    enrollmentStatus = FaceEnrollmentStatus.ACTIVE,
                    consentGiven = true,
                    consentGivenAt = now,
                    imageReference = stored.reference,
                    embeddingReference = null,
                    enrolledAt = now,
                    resetAt = null,
                )
            )
        }
        return profile.toView(message = ACTIVE_MESSAGE)
    }

    /**
     * Reset clears references and best-effort deletes the local enrollment file.
     * The row is kept so audit history (consent timestamps, previous enroll/reset
     * activity) remains visible.
     */
    @Transactional
    fun reset(tenantId: UUID, userId: UUID): FaceProfileView {
        requireMember(tenantId, userId)
        val profile = repository.findByTenantIdAndUserId(tenantId, userId)
            ?: throw DomainException.notFound("FaceProfile", userId)

        val previousReference = profile.imageReference
        profile.imageReference = null
        profile.embeddingReference = null
        profile.enrollmentStatus = FaceEnrollmentStatus.RESET
        profile.resetAt = Instant.now()
        val saved = repository.save(profile)

        if (previousReference != null) {
            storage.delete(previousReference)
        }
        return saved.toView(message = RESET_MESSAGE)
    }

    private fun requireMember(tenantId: UUID, userId: UUID) {
        val membership = memberships.findByTenantIdAndUserId(tenantId, userId)
            ?: throw DomainException.notFound("Membership", userId)
        if (!membership.active) {
            throw DomainException(ErrorCode.NOT_FOUND, "Anggota tidak aktif di tenant ini")
        }
    }

    private fun emptyView(): FaceProfileView = FaceProfileView(
        profileId = EMPTY_PROFILE_ID,
        enrollmentStatus = FaceEnrollmentStatus.PENDING,
        consentGiven = false,
        imageStored = false,
        enrolledAt = null,
        resetAt = null,
        updatedAt = Instant.now(),
        message = PENDING_MESSAGE,
    )

    private fun UserFaceProfile.statusMessage(): String = when (enrollmentStatus) {
        FaceEnrollmentStatus.ACTIVE -> ACTIVE_MESSAGE
        FaceEnrollmentStatus.RESET -> RESET_MESSAGE
        FaceEnrollmentStatus.PENDING -> PENDING_MESSAGE
    }

    companion object {
        // Used only for the empty-state view when no profile row exists yet.
        private val EMPTY_PROFILE_ID: UUID = UUID(0L, 0L)

        const val ACTIVE_MESSAGE =
            "Foto enrollment tersimpan. Status ACTIVE berarti gambar dan persetujuan tercatat, " +
                "bukan berarti pencocokan wajah aktif (belum diimplementasikan)."
        const val PENDING_MESSAGE =
            "Belum ada enrollment wajah untuk pengguna ini."
        const val RESET_MESSAGE =
            "Enrollment wajah direset. Referensi dibersihkan dan file lokal dicoba dihapus."
    }
}

internal fun UserFaceProfile.toView(message: String): FaceProfileView = FaceProfileView(
    profileId = id ?: error("face profile id null"),
    enrollmentStatus = enrollmentStatus,
    consentGiven = consentGiven,
    imageStored = imageReference != null,
    enrolledAt = enrolledAt,
    resetAt = resetAt,
    updatedAt = updatedAt,
    message = message,
)
