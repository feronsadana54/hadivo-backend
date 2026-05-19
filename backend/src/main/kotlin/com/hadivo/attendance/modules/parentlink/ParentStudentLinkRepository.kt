package com.hadivo.attendance.modules.parentlink

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ParentStudentLinkRepository : JpaRepository<ParentStudentLink, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<ParentStudentLink>
    fun findAllByTenantIdAndStudentUserIdAndActive(
        tenantId: UUID,
        studentUserId: UUID,
        active: Boolean,
    ): List<ParentStudentLink>
}
