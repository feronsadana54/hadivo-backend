package com.hadivo.attendance.modules.membership

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.security.AuthPrincipal
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MembershipGuard(private val memberships: MembershipRepository) {

    fun requireMember(principal: AuthPrincipal, tenantId: UUID): Membership {
        val membership = memberships.findByTenantIdAndUserId(tenantId, principal.userId)
            ?: throw DomainException(ErrorCode.FORBIDDEN, "Tidak terdaftar di tenant ini")
        if (!membership.active) {
            throw DomainException(ErrorCode.FORBIDDEN, "Membership tidak aktif")
        }
        return membership
    }

    fun requireRole(principal: AuthPrincipal, tenantId: UUID, vararg allowed: Role): Membership {
        val membership = requireMember(principal, tenantId)
        if (membership.role !in allowed) {
            throw DomainException(ErrorCode.FORBIDDEN, "Role tidak memiliki izin")
        }
        return membership
    }

    fun requireAdmin(principal: AuthPrincipal, tenantId: UUID): Membership =
        requireRole(principal, tenantId, Role.TENANT_ADMIN, Role.SUPER_ADMIN)
}
