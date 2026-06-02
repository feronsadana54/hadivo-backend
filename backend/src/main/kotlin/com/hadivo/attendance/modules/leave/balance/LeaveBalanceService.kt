package com.hadivo.attendance.modules.leave.balance

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.auth.UserRepository
import com.hadivo.attendance.modules.calendar.WorkdayCalendarService
import com.hadivo.attendance.modules.leave.LeaveRequest
import com.hadivo.attendance.modules.leave.LeaveRequestType
import com.hadivo.attendance.modules.membership.MembershipRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service
class LeaveBalanceService(
    private val balances: LeaveBalanceRepository,
    private val ledgers: LeaveBalanceLedgerRepository,
    private val policyService: LeavePolicyService,
    private val users: UserRepository,
    private val memberships: MembershipRepository,
    private val audit: AuditLogger,
    private val workdayCalendar: WorkdayCalendarService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun getOrInitialize(tenantId: UUID, userId: UUID, year: Int): LeaveBalance {
        balances.findByTenantIdAndUserIdAndYear(tenantId, userId, year)?.let { return it }

        val policy = policyService.getOrCreateDefault(tenantId)
        val quota = policy.annualLeaveQuotaDays.setScale(2, RoundingMode.HALF_UP)
        val balance = balances.save(
            LeaveBalance(
                tenantId = tenantId,
                userId = userId,
                year = year,
                annualQuotaDays = quota,
                usedDays = BigDecimal("0.00"),
                adjustedDays = BigDecimal("0.00"),
                remainingDays = quota,
            )
        )

        ledgers.save(
            LeaveBalanceLedger(
                tenantId = tenantId,
                userId = userId,
                leaveRequestId = null,
                year = year,
                changeType = LeaveBalanceChangeType.INITIAL,
                daysChanged = quota,
                balanceBefore = BigDecimal("0.00"),
                balanceAfter = quota,
                note = "Initialized from policy",
                createdBy = null,
            )
        )

        audit.log(
            tenantId = tenantId,
            actorUserId = null,
            action = "LEAVE_BALANCE_INITIALIZED",
            resourceType = "LeaveBalance",
            resourceId = balance.id?.toString(),
            metadata = mapOf(
                "userId" to userId.toString(),
                "year" to year,
                "annualQuotaDays" to quota.toPlainString(),
            ),
        )

        return balance
    }

    @Transactional(readOnly = true)
    fun listByTenantAndYear(tenantId: UUID, year: Int): List<LeaveBalanceView> {
        val rows = balances.findAllByTenantIdAndYear(tenantId, year)
        return rows.map { it.toView() }
    }

    @Transactional
    fun getView(tenantId: UUID, userId: UUID, year: Int): LeaveBalanceView {
        requireMemberInTenant(tenantId, userId)
        return getOrInitialize(tenantId, userId, year).toView()
    }

    @Transactional
    fun adjust(
        tenantId: UUID,
        userId: UUID,
        request: AdjustLeaveBalanceRequest,
        actorUserId: UUID,
    ): LeaveBalanceView {
        val year = request.year
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Tahun wajib diisi")
        val days = request.days
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Jumlah hari penyesuaian wajib diisi")
        val note = request.note?.trim()?.takeIf { it.isNotBlank() }
            ?: throw DomainException(ErrorCode.VALIDATION_FAILED, "Catatan penyesuaian wajib diisi")

        val currentYear = LocalDate.now().year
        if (year < currentYear - 1 || year > currentYear + 1) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Tahun penyesuaian harus antara ${currentYear - 1} dan ${currentYear + 1}",
            )
        }
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            throw DomainException(ErrorCode.VALIDATION_FAILED, "Jumlah hari tidak boleh nol")
        }

        requireMemberInTenant(tenantId, userId)
        val normalized = days.setScale(2, RoundingMode.HALF_UP)
        val balance = getOrInitialize(tenantId, userId, year)
        val before = balance.remainingDays
        val newAdjusted = (balance.adjustedDays + normalized).setScale(2, RoundingMode.HALF_UP)
        val projected = (balance.annualQuotaDays + newAdjusted - balance.usedDays)
            .setScale(2, RoundingMode.HALF_UP)
        if (projected < BigDecimal.ZERO) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Penyesuaian membuat sisa cuti menjadi negatif",
                mapOf("remaining" to before.toPlainString(), "requested" to normalized.toPlainString()),
            )
        }

        balance.adjustedDays = newAdjusted
        balance.recomputeRemaining()
        val saved = balances.save(balance)

        ledgers.save(
            LeaveBalanceLedger(
                tenantId = tenantId,
                userId = userId,
                leaveRequestId = null,
                year = year,
                changeType = LeaveBalanceChangeType.ADJUST,
                daysChanged = normalized,
                balanceBefore = before,
                balanceAfter = saved.remainingDays,
                note = note,
                createdBy = actorUserId,
            )
        )

        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "LEAVE_BALANCE_ADJUSTED",
            resourceType = "LeaveBalance",
            resourceId = saved.id?.toString(),
            metadata = mapOf(
                "userId" to userId.toString(),
                "year" to year,
                "daysChanged" to normalized.toPlainString(),
                "balanceBefore" to before.toPlainString(),
                "balanceAfter" to saved.remainingDays.toPlainString(),
            ),
        )

        return saved.toView()
    }

    /**
     * Deduct annual leave days for an approved ANNUAL_LEAVE request.
     * Called from LeaveRequestService.review() inside the parent transaction.
     * Throws DomainException if balance insufficient or cross-year — caller
     * relies on the parent @Transactional to roll back the approval.
     *
     * Idempotent: if a DEDUCT ledger already exists for this leave_request_id,
     * the deduct is skipped (defence-in-depth on top of the status guard).
     */
    @Transactional
    fun deductForApproval(request: LeaveRequest, reviewerUserId: UUID) {
        val leaveRequestId = request.id
            ?: error("leave request id null when deducting balance")
        if (request.requestType != LeaveRequestType.ANNUAL_LEAVE) return

        if (ledgers.existsByLeaveRequestIdAndChangeType(leaveRequestId, LeaveBalanceChangeType.DEDUCT)) {
            log.info("Leave balance deduct already exists for leaveRequestId={}, skipping", leaveRequestId)
            return
        }

        val startYear = request.startDate.year
        val endYear = request.endDate.year
        if (startYear != endYear) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Pengajuan cuti lintas tahun belum didukung.",
            )
        }

        val workdays = workdayCalendar.countWorkdays(
            request.tenantId, request.startDate, request.endDate,
        )
        if (workdays <= 0) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Pengajuan cuti tidak memiliki hari kerja yang dapat dipotong.",
            )
        }
        val days = BigDecimal(workdays).setScale(2, RoundingMode.HALF_UP)
        val balance = getOrInitialize(request.tenantId, request.requesterUserId, startYear)
        if (balance.remainingDays < days) {
            throw DomainException(
                ErrorCode.VALIDATION_FAILED,
                "Sisa cuti tahunan tidak mencukupi untuk pengajuan ini.",
                mapOf(
                    "remaining" to balance.remainingDays.toPlainString(),
                    "requested" to days.toPlainString(),
                ),
            )
        }

        val before = balance.remainingDays
        balance.usedDays = (balance.usedDays + days).setScale(2, RoundingMode.HALF_UP)
        balance.recomputeRemaining()
        val saved = balances.save(balance)

        ledgers.save(
            LeaveBalanceLedger(
                tenantId = request.tenantId,
                userId = request.requesterUserId,
                leaveRequestId = leaveRequestId,
                year = startYear,
                changeType = LeaveBalanceChangeType.DEDUCT,
                daysChanged = days,
                balanceBefore = before,
                balanceAfter = saved.remainingDays,
                note = null,
                createdBy = reviewerUserId,
            )
        )

        audit.log(
            tenantId = request.tenantId,
            actorUserId = reviewerUserId,
            action = "LEAVE_BALANCE_DEDUCTED",
            resourceType = "LeaveBalance",
            resourceId = saved.id?.toString(),
            metadata = mapOf(
                "userId" to request.requesterUserId.toString(),
                "year" to startYear,
                "daysChanged" to days.toPlainString(),
                "balanceBefore" to before.toPlainString(),
                "balanceAfter" to saved.remainingDays.toPlainString(),
                "leaveRequestId" to leaveRequestId.toString(),
            ),
        )
    }

    private fun requireMemberInTenant(tenantId: UUID, userId: UUID) {
        val membership = memberships.findByTenantIdAndUserId(tenantId, userId)
            ?: throw DomainException.notFound("Membership", userId)
        if (!membership.active) {
            throw DomainException(ErrorCode.NOT_FOUND, "Anggota tidak aktif di tenant ini")
        }
    }

    fun LeaveBalance.toView(): LeaveBalanceView {
        val user = users.findById(userId).orElse(null)
        return LeaveBalanceView(
            id = id ?: error("leave balance id null"),
            tenantId = tenantId,
            userId = userId,
            userFullName = user?.fullName,
            userEmail = user?.email,
            year = year,
            annualQuotaDays = annualQuotaDays,
            usedDays = usedDays,
            adjustedDays = adjustedDays,
            remainingDays = remainingDays,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
