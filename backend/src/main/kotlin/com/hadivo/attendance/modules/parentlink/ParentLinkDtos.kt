package com.hadivo.attendance.modules.parentlink

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreateParentLinkRequest(
    @field:NotNull val parentUserId: UUID,
    @field:NotNull val studentUserId: UUID,
    @field:NotBlank val relationship: String,
)

data class ParentLinkView(
    val id: UUID,
    val tenantId: UUID,
    val parentUserId: UUID,
    val studentUserId: UUID,
    val relationship: String,
    val active: Boolean,
)
