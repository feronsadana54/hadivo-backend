package com.hadivo.attendance.modules.device

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserDeviceRepository : JpaRepository<UserDevice, UUID> {
    @Query(
        """
        select d
        from UserDevice d
        where d.tenantId = :tenantId
          and d.userId = :userId
          and d.trusted = true
          and d.active = true
        order by d.firstSeenAt asc
        """
    )
    fun findActiveTrusted(
        @Param("tenantId") tenantId: UUID,
        @Param("userId") userId: UUID,
    ): List<UserDevice>

    fun findAllByTenantIdAndUserIdOrderByLastSeenAtDesc(tenantId: UUID, userId: UUID): List<UserDevice>
}
