package com.hadivo.attendance.modules.audit

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class AuditLogger(private val repository: AuditLogRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun log(
        tenantId: UUID?,
        actorUserId: UUID?,
        action: String,
        resourceType: String,
        resourceId: String? = null,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        repository.save(
            AuditLog(
                tenantId = tenantId,
                actorUserId = actorUserId,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                metadata = metadata.ifEmpty { null },
            )
        )
    }
}
