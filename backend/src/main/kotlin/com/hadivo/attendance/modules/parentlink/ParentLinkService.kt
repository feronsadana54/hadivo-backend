package com.hadivo.attendance.modules.parentlink

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.modules.membership.MembershipRepository
import com.hadivo.attendance.modules.membership.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ParentLinkService(
    private val links: ParentStudentLinkRepository,
    private val memberships: MembershipRepository,
) {

    @Transactional
    fun create(tenantId: UUID, request: CreateParentLinkRequest): ParentStudentLink {
        if (request.parentUserId == request.studentUserId) {
            throw DomainException.conflict("Parent dan student tidak boleh user yang sama")
        }
        requireRole(tenantId, request.parentUserId, Role.PARENT)
        requireRole(tenantId, request.studentUserId, Role.STUDENT)
        return links.save(
            ParentStudentLink(
                tenantId = tenantId,
                parentUserId = request.parentUserId,
                studentUserId = request.studentUserId,
                relationship = request.relationship,
            )
        )
    }

    fun list(tenantId: UUID): List<ParentStudentLink> = links.findAllByTenantId(tenantId)

    fun activeParentsOf(tenantId: UUID, studentUserId: UUID): List<UUID> =
        links.findAllByTenantIdAndStudentUserIdAndActive(tenantId, studentUserId, true)
            .map { it.parentUserId }

    @Transactional
    fun remove(tenantId: UUID, linkId: UUID) {
        val link = links.findById(linkId)
            .orElseThrow { DomainException.notFound("Parent link", linkId) }
        if (link.tenantId != tenantId) {
            throw DomainException.notFound("Parent link", linkId)
        }
        links.delete(link)
    }

    private fun requireRole(tenantId: UUID, userId: UUID, role: Role) {
        val membership = memberships.findByTenantIdAndUserId(tenantId, userId)
            ?: throw DomainException.notFound("Membership", userId)
        if (membership.role != role) {
            throw DomainException.conflict("User $userId tidak memiliki role $role di tenant ini")
        }
    }
}

fun ParentStudentLink.toView(): ParentLinkView = ParentLinkView(
    id = id ?: error("Link belum tersimpan"),
    tenantId = tenantId,
    parentUserId = parentUserId,
    studentUserId = studentUserId,
    relationship = relationship,
    active = active,
)
