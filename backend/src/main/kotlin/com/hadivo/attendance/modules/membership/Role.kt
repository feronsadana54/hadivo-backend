package com.hadivo.attendance.modules.membership

enum class Role {
    SUPER_ADMIN,
    TENANT_ADMIN,
    MANAGER,
    TEACHER,
    EMPLOYEE,
    STUDENT,
    PARENT;

    fun isTenantAdminLevel(): Boolean = this == TENANT_ADMIN || this == SUPER_ADMIN

    fun canManageMembers(): Boolean = this == TENANT_ADMIN || this == SUPER_ADMIN || this == MANAGER
}
