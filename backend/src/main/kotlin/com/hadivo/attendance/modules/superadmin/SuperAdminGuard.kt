package com.hadivo.attendance.modules.superadmin

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import org.springframework.stereotype.Component

@Component
class SuperAdminGuard(private val memberships: MembershipRepository) {

    fun requireSuperAdmin(principal: AuthPrincipal) {
        val allowed = memberships.existsByUserIdAndRoleAndActive(
            userId = principal.userId,
            role = Role.SUPER_ADMIN,
            active = true,
        )
        if (!allowed) {
            throw DomainException(ErrorCode.FORBIDDEN, "Role tidak memiliki izin Super Admin")
        }
    }
}
