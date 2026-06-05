package com.hadivo.attendance.modules.face

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserFaceProfileRepository : JpaRepository<UserFaceProfile, UUID> {
    fun findByTenantIdAndUserId(tenantId: UUID, userId: UUID): UserFaceProfile?
}
