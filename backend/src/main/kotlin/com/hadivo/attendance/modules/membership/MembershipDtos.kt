package com.hadivo.attendance.modules.membership

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreateMembershipRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val role: Role,
)

data class MembershipView(
    val id: UUID,
    val tenantId: UUID,
    val userId: UUID,
    val role: Role,
    val active: Boolean,
)
