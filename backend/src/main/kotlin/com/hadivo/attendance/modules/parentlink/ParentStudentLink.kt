package com.hadivo.attendance.modules.parentlink

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "parent_student_links")
class ParentStudentLink(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "parent_user_id", nullable = false)
    var parentUserId: UUID,

    @Column(name = "student_user_id", nullable = false)
    var studentUserId: UUID,

    @Column(nullable = false)
    var relationship: String,

    @Column(nullable = false)
    var active: Boolean = true,
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
