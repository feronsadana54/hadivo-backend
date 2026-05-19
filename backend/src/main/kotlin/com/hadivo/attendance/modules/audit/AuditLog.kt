package com.hadivo.attendance.modules.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Column(name = "tenant_id")
    var tenantId: UUID? = null,

    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,

    @Column(nullable = false)
    var action: String,

    @Column(name = "resource_type", nullable = false)
    var resourceType: String,

    @Column(name = "resource_id")
    var resourceId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    var metadata: Map<String, Any?>? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
