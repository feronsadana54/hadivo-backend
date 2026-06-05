package com.hadivo.attendance.modules.face

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_face_profiles")
class UserFaceProfile(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 20)
    var enrollmentStatus: FaceEnrollmentStatus = FaceEnrollmentStatus.PENDING,

    @Column(name = "consent_given", nullable = false)
    var consentGiven: Boolean = false,

    @Column(name = "consent_given_at")
    var consentGivenAt: Instant? = null,

    @Column(name = "image_reference", length = 255)
    var imageReference: String? = null,

    @Column(name = "embedding_reference", length = 255)
    var embeddingReference: String? = null,

    @Column(name = "enrolled_at")
    var enrolledAt: Instant? = null,

    @Column(name = "reset_at")
    var resetAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touchUpdatedAt() {
        updatedAt = Instant.now()
    }
}
