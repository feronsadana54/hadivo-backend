package com.hadivo.attendance.modules.leave.balance

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class LeavePolicyService(
    private val repository: LeavePolicyRepository,
) {

    @Transactional
    fun getOrCreateDefault(tenantId: UUID): LeavePolicy {
        repository.findByTenantId(tenantId)?.let { return it }
        return repository.save(LeavePolicy(tenantId = tenantId))
    }

    @Transactional
    fun update(tenantId: UUID, request: UpdateLeavePolicyRequest): Pair<LeavePolicy, List<String>> {
        val policy = getOrCreateDefault(tenantId)
        val changed = mutableListOf<String>()

        request.name?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (policy.name != it) {
                policy.name = it
                changed.add("name")
            }
        }
        request.annualLeaveQuotaDays?.let { value ->
            val normalized = value.setScale(2, RoundingMode.HALF_UP)
            if (normalized < BigDecimal.ZERO || normalized > LeavePolicy.MAX_QUOTA) {
                throw DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "Kuota cuti tahunan harus antara 0 dan ${LeavePolicy.MAX_QUOTA.toPlainString()} hari",
                )
            }
            if (policy.annualLeaveQuotaDays.compareTo(normalized) != 0) {
                policy.annualLeaveQuotaDays = normalized
                changed.add("annualLeaveQuotaDays")
            }
        }
        request.sickLeaveRequiresBalance?.let {
            if (policy.sickLeaveRequiresBalance != it) {
                policy.sickLeaveRequiresBalance = it
                changed.add("sickLeaveRequiresBalance")
            }
        }
        request.permissionRequiresBalance?.let {
            if (policy.permissionRequiresBalance != it) {
                policy.permissionRequiresBalance = it
                changed.add("permissionRequiresBalance")
            }
        }
        request.businessTripRequiresBalance?.let {
            if (policy.businessTripRequiresBalance != it) {
                policy.businessTripRequiresBalance = it
                changed.add("businessTripRequiresBalance")
            }
        }
        request.active?.let {
            if (policy.active != it) {
                policy.active = it
                changed.add("active")
            }
        }

        val saved = repository.save(policy)
        return saved to changed
    }
}

fun LeavePolicy.toView(): LeavePolicyView = LeavePolicyView(
    id = id ?: error("policy id null"),
    tenantId = tenantId,
    name = name,
    annualLeaveQuotaDays = annualLeaveQuotaDays,
    sickLeaveRequiresBalance = sickLeaveRequiresBalance,
    permissionRequiresBalance = permissionRequiresBalance,
    businessTripRequiresBalance = businessTripRequiresBalance,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
