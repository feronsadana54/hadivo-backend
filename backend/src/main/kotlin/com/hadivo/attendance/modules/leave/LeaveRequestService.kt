package com.hadivo.attendance.modules.leave

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.common.security.AuthPrincipal
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.leave.balance.LeaveBalanceService
import com.hadivo.attendance.modules.leave.correction.CorrectionApplyService
import com.hadivo.attendance.modules.membership.MembershipGuard
import com.hadivo.attendance.modules.membership.Role
import com.hadivo.attendance.modules.notification.NotificationEventType
import com.hadivo.attendance.modules.notification.NotificationPublisher
import com.hadivo.attendance.modules.notification.NotificationRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class LeaveRequestService(
    private val repository: LeaveRequestRepository,
    private val users: UserRepository,
    private val guard: MembershipGuard,
    private val audit: AuditLogger,
    private val publisher: NotificationPublisher,
    private val correctionApply: CorrectionApplyService,
    private val balanceService: LeaveBalanceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(principal: AuthPrincipal, tenantId: UUID): List<LeaveRequestView> {
        val membership = guard.requireMember(principal, tenantId)
        val rows = if (membership.role in REVIEWER_ROLES) {
            repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
        } else {
            repository.findAllByTenantIdAndRequesterUserIdOrderByCreatedAtDesc(tenantId, principal.userId)
        }
        return rows.map { it.toView() }
    }

    @Transactional(readOnly = true)
    fun get(principal: AuthPrincipal, tenantId: UUID, requestId: UUID): LeaveRequestView {
        val membership = guard.requireMember(principal, tenantId)
        val request = repository.findByIdAndTenantId(requestId, tenantId)
            ?: throw DomainException.notFound("Leave request", requestId)
        if (membership.role !in REVIEWER_ROLES && request.requesterUserId != principal.userId) {
            throw DomainException.forbidden("Tidak dapat melihat pengajuan ini")
        }
        return request.toView()
    }

    @Transactional
    fun create(principal: AuthPrincipal, tenantId: UUID, payload: CreateLeaveRequestRequest): LeaveRequestView {
        val membership = guard.requireMember(principal, tenantId)
        if (membership.role !in REQUESTER_ROLES) {
            throw DomainException.forbidden("Role Anda belum dapat mengajukan permintaan")
        }

        val type = payload.requestType
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Jenis pengajuan wajib diisi")
        val startDate = payload.startDate
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal mulai wajib diisi")
        val endDate = payload.endDate
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal selesai wajib diisi")

        if (endDate.isBefore(startDate)) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Tanggal mulai tidak boleh setelah tanggal selesai")
        }
        val days = ChronoUnit.DAYS.between(startDate, endDate) + 1
        if (days > MAX_RANGE_DAYS) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Rentang pengajuan maksimal $MAX_RANGE_DAYS hari",
                mapOf("startDate" to startDate, "endDate" to endDate, "maxDays" to MAX_RANGE_DAYS),
            )
        }

        val reason = payload.reason?.trim()?.takeIf { it.isNotBlank() }
        if (type != LeaveRequestType.ATTENDANCE_CORRECTION && reason == null) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Alasan wajib diisi untuk jenis pengajuan ini")
        }

        val correctionIn = payload.requestedClockInTime
        val correctionOut = payload.requestedClockOutTime
        if (type == LeaveRequestType.ATTENDANCE_CORRECTION && correctionIn == null && correctionOut == null) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Minimal salah satu jam clock-in atau clock-out wajib diisi untuk koreksi absensi",
            )
        }

        if (type != LeaveRequestType.ATTENDANCE_CORRECTION) {
            val overlaps = repository.findActiveOverlaps(
                tenantId = tenantId,
                requesterUserId = principal.userId,
                startDate = startDate,
                endDate = endDate,
                excludeId = null,
            )
            if (overlaps.isNotEmpty()) {
                throw DomainException.conflict("Sudah ada pengajuan aktif yang beririsan pada rentang tanggal tersebut")
            }
        }

        val saved = repository.save(
            LeaveRequest(
                tenantId = tenantId,
                requesterUserId = principal.userId,
                requestType = type,
                startDate = startDate,
                endDate = endDate,
                reason = reason,
                requestedClockInAt = if (type == LeaveRequestType.ATTENDANCE_CORRECTION) correctionIn else null,
                requestedClockOutAt = if (type == LeaveRequestType.ATTENDANCE_CORRECTION) correctionOut else null,
                correctionNote = if (type == LeaveRequestType.ATTENDANCE_CORRECTION) {
                    payload.correctionNote?.trim()?.takeIf { it.isNotBlank() }
                } else {
                    null
                },
            )
        )

        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "LEAVE_REQUEST_CREATED",
            resourceType = "LeaveRequest",
            resourceId = saved.id?.toString(),
            metadata = mapOf(
                "requestType" to saved.requestType.name,
                "startDate" to saved.startDate.toString(),
                "endDate" to saved.endDate.toString(),
                "reasonProvided" to (saved.reason != null),
            ),
        )

        publishSafely(saved, NotificationEventType.LEAVE_REQUEST_CREATED, principal.userId)
        return saved.toView()
    }

    @Transactional
    fun approve(
        principal: AuthPrincipal,
        tenantId: UUID,
        requestId: UUID,
        payload: ReviewLeaveRequestRequest,
    ): LeaveRequestView = review(principal, tenantId, requestId, payload, LeaveRequestStatus.APPROVED)

    @Transactional
    fun reject(
        principal: AuthPrincipal,
        tenantId: UUID,
        requestId: UUID,
        payload: ReviewLeaveRequestRequest,
    ): LeaveRequestView = review(principal, tenantId, requestId, payload, LeaveRequestStatus.REJECTED)

    @Transactional
    fun cancel(principal: AuthPrincipal, tenantId: UUID, requestId: UUID): LeaveRequestView {
        guard.requireMember(principal, tenantId)
        val request = repository.findByIdAndTenantId(requestId, tenantId)
            ?: throw DomainException.notFound("Leave request", requestId)

        if (request.requesterUserId != principal.userId) {
            throw DomainException.forbidden("Hanya pengaju yang dapat membatalkan pengajuan ini")
        }
        if (request.status != LeaveRequestStatus.PENDING) {
            throw DomainException.conflict("Pengajuan tidak dapat dibatalkan karena bukan PENDING")
        }

        request.status = LeaveRequestStatus.CANCELLED
        val saved = repository.save(request)

        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = "LEAVE_REQUEST_CANCELLED",
            resourceType = "LeaveRequest",
            resourceId = saved.id?.toString(),
            metadata = mapOf("requestType" to saved.requestType.name),
        )
        publishSafely(saved, NotificationEventType.LEAVE_REQUEST_CANCELLED, principal.userId)
        return saved.toView()
    }

    private fun review(
        principal: AuthPrincipal,
        tenantId: UUID,
        requestId: UUID,
        payload: ReviewLeaveRequestRequest,
        nextStatus: LeaveRequestStatus,
    ): LeaveRequestView {
        guard.requireRole(principal, tenantId, *REVIEWER_ROLES.toTypedArray())
        val request = repository.findByIdAndTenantId(requestId, tenantId)
            ?: throw DomainException.notFound("Leave request", requestId)
        if (request.status != LeaveRequestStatus.PENDING) {
            throw DomainException.conflict("Pengajuan sudah diproses sebelumnya")
        }

        request.status = nextStatus
        request.reviewerUserId = principal.userId
        request.reviewedAt = Instant.now()
        request.reviewNote = payload.reviewNote?.trim()?.takeIf { it.isNotBlank() }
        val saved = repository.save(request)

        // For ATTENDANCE_CORRECTION approvals, the correction must be applied
        // to attendance_records in the same transaction. If apply throws, the
        // surrounding @Transactional rolls back the status change above and
        // the request stays PENDING.
        if (nextStatus == LeaveRequestStatus.APPROVED && saved.requestType.isAttendanceCorrection()) {
            correctionApply.apply(saved, principal.userId)
        }

        // For ANNUAL_LEAVE approvals, deduct from balance in the same transaction.
        // Insufficient balance or cross-year requests throw, rolling back approve.
        if (nextStatus == LeaveRequestStatus.APPROVED && saved.requestType == LeaveRequestType.ANNUAL_LEAVE) {
            balanceService.deductForApproval(saved, principal.userId)
        }

        val action = if (nextStatus == LeaveRequestStatus.APPROVED) "LEAVE_REQUEST_APPROVED"
        else "LEAVE_REQUEST_REJECTED"
        audit.log(
            tenantId = tenantId,
            actorUserId = principal.userId,
            action = action,
            resourceType = "LeaveRequest",
            resourceId = saved.id?.toString(),
            metadata = mapOf(
                "requestType" to saved.requestType.name,
                "reviewNoteProvided" to (saved.reviewNote != null),
            ),
        )

        if (nextStatus == LeaveRequestStatus.APPROVED && saved.requestType.isAttendanceCorrection()) {
            audit.log(
                tenantId = tenantId,
                actorUserId = principal.userId,
                action = "ATTENDANCE_CORRECTION_APPROVED",
                resourceType = "LeaveRequest",
                resourceId = saved.id?.toString(),
                metadata = mapOf(
                    "startDate" to saved.startDate.toString(),
                    "requestedClockInProvided" to (saved.requestedClockInAt != null),
                    "requestedClockOutProvided" to (saved.requestedClockOutAt != null),
                ),
            )
        }

        val event = if (nextStatus == LeaveRequestStatus.APPROVED) NotificationEventType.LEAVE_REQUEST_APPROVED
        else NotificationEventType.LEAVE_REQUEST_REJECTED
        publishSafely(saved, event, principal.userId)
        return saved.toView()
    }

    private fun publishSafely(request: LeaveRequest, event: NotificationEventType, actorUserId: UUID) {
        try {
            publisher.publish(
                NotificationRequest(
                    eventType = event,
                    tenantId = request.tenantId,
                    actorUserId = actorUserId,
                    recipientUserIds = listOf(request.requesterUserId),
                    occurredAt = Instant.now(),
                    metadata = mapOf(
                        "leaveRequestId" to request.id?.toString(),
                        "requestType" to request.requestType.name,
                        "status" to request.status.name,
                        "startDate" to request.startDate.toString(),
                        "endDate" to request.endDate.toString(),
                    ),
                )
            )
        } catch (ex: Exception) {
            log.warn("Failed to publish leave notification {}: {}", event, ex.message)
        }
    }

    private fun LeaveRequest.toView(): LeaveRequestView {
        val requester = users.findById(requesterUserId).orElse(null)
        val reviewer = reviewerUserId?.let { users.findById(it).orElse(null) }
        return LeaveRequestView(
            id = id ?: error("leave request id null"),
            tenantId = tenantId,
            requesterUserId = requesterUserId,
            requesterFullName = requester?.fullName,
            requesterEmail = requester?.email,
            requestType = requestType,
            startDate = startDate,
            endDate = endDate,
            reason = reason,
            status = status,
            reviewerUserId = reviewerUserId,
            reviewerFullName = reviewer?.fullName,
            reviewedAt = reviewedAt,
            reviewNote = reviewNote,
            attachmentUrl = attachmentUrl,
            requestedClockInTime = requestedClockInAt,
            requestedClockOutTime = requestedClockOutAt,
            correctionNote = correctionNote,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        const val MAX_RANGE_DAYS = 31L
        val REVIEWER_ROLES: Set<Role> = setOf(Role.TENANT_ADMIN, Role.SUPER_ADMIN)
        val REQUESTER_ROLES: Set<Role> = setOf(
            Role.TENANT_ADMIN,
            Role.MANAGER,
            Role.TEACHER,
            Role.EMPLOYEE,
            Role.STUDENT,
        )
    }
}
