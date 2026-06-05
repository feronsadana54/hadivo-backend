package com.hadivo.attendance.modules.face

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.response.ApiResponse
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.common.security.CurrentUser
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class FaceEnrollmentController(
    private val service: FaceEnrollmentService,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
) {

    @GetMapping("/members/{userId}/face-profile")
    fun get(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<FaceProfileView> {
        val membership = guard.requireMember(principal, tenantId)
        if (membership.role !in ADMIN_ROLES && userId != principal.userId) {
            throw DomainException.forbidden("Hanya dapat melihat profil wajah milik sendiri")
        }
        return ApiResponse.ok(service.getOrEmpty(tenantId, userId))
    }

    @PostMapping("/members/{userId}/face-profile/enroll")
    fun enroll(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: EnrollFaceRequest,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<FaceProfileView> {
        val membership = guard.requireMember(principal, tenantId)
        if (membership.role !in ADMIN_ROLES && userId != principal.userId) {
            throw DomainException.forbidden("Hanya dapat melakukan enrollment untuk diri sendiri")
        }
        val view = service.enroll(tenantId, userId, request)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "FACE_PROFILE_ENROLLED",
            resourceType = "UserFaceProfile",
            resourceId = view.profileId.toString(),
            // Privacy: do NOT log imageBase64, imageReference, embeddingReference,
            // or absolute path. Only enough to attribute the action.
            metadata = mapOf(
                "userId" to userId.toString(),
                "enrollmentStatus" to view.enrollmentStatus.name,
                "consentGiven" to view.consentGiven,
                "imageStored" to view.imageStored,
                "profileId" to view.profileId.toString(),
            ),
        )
        return ApiResponse.ok(view)
    }

    @PostMapping("/members/{userId}/face-profile/reset")
    fun reset(
        @PathVariable tenantId: UUID,
        @PathVariable userId: UUID,
        @CurrentUser principal: AuthPrincipal,
    ): ApiResponse<FaceProfileView> {
        guard.requireAdmin(principal, tenantId)
        val view = service.reset(tenantId, userId)
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "FACE_PROFILE_RESET",
            resourceType = "UserFaceProfile",
            resourceId = view.profileId.toString(),
            metadata = mapOf(
                "userId" to userId.toString(),
                "enrollmentStatus" to view.enrollmentStatus.name,
                "consentGiven" to view.consentGiven,
                "imageStored" to view.imageStored,
                "profileId" to view.profileId.toString(),
            ),
        )
        return ApiResponse.ok(view)
    }

    companion object {
        val ADMIN_ROLES = setOf(Role.TENANT_ADMIN, Role.SUPER_ADMIN)
    }
}
