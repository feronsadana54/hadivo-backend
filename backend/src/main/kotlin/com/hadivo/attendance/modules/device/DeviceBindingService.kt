package com.hadivo.attendance.modules.device

import com.hadivo.attendance.common.exception.DomainException
import com.hadivo.attendance.common.exception.ErrorCode
import com.hadivo.attendance.modules.attendance.AttemptLogger
import com.hadivo.attendance.modules.attendance.AttemptReason
import com.hadivo.attendance.modules.attendance.AttendanceType
import com.hadivo.attendance.modules.audit.AuditLogger
import com.hadivo.attendance.modules.membership.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceBindingService(
    private val devices: UserDeviceRepository,
    private val memberships: MembershipRepository,
    private val attemptLogger: AttemptLogger,
    private val audit: AuditLogger,
) {

    @Transactional
    fun ensureAllowedForAttendance(
        tenantId: UUID,
        userId: UUID,
        type: AttendanceType,
        command: DeviceBindingCommand,
        latitude: Double?,
        longitude: Double?,
    ): DeviceBindingResult {
        val deviceId = normalizeDeviceId(command.deviceId)
            ?: reject(
                tenantId = tenantId,
                userId = userId,
                type = type,
                reason = AttemptReason.INVALID_DEVICE,
                errorCode = ErrorCode.INVALID_DEVICE,
                message = "Perangkat tidak valid untuk absensi. Buka ulang aplikasi dan coba lagi.",
                latitude = latitude,
                longitude = longitude,
                rawDeviceId = command.deviceId,
            )

        val now = Instant.now()
        val activeTrusted = devices.findActiveTrusted(tenantId, userId).firstOrNull()
        if (activeTrusted == null) {
            val device = devices.save(
                UserDevice(
                    tenantId = tenantId,
                    userId = userId,
                    deviceId = deviceId,
                    deviceName = command.deviceName.clean(MAX_DEVICE_NAME_LENGTH),
                    platform = command.platform.clean(MAX_PLATFORM_LENGTH),
                    trusted = true,
                    active = true,
                    firstSeenAt = now,
                    lastSeenAt = now,
                )
            )
            return DeviceBindingResult(device = device, registered = true)
        }

        if (activeTrusted.deviceId == deviceId) {
            activeTrusted.lastSeenAt = now
            activeTrusted.deviceName = command.deviceName.clean(MAX_DEVICE_NAME_LENGTH) ?: activeTrusted.deviceName
            activeTrusted.platform = command.platform.clean(MAX_PLATFORM_LENGTH) ?: activeTrusted.platform
            return DeviceBindingResult(device = devices.save(activeTrusted), registered = false)
        }

        reject(
            tenantId = tenantId,
            userId = userId,
            type = type,
            reason = AttemptReason.DEVICE_MISMATCH,
            errorCode = ErrorCode.DEVICE_MISMATCH,
            message = DEVICE_MISMATCH_MESSAGE,
            latitude = latitude,
            longitude = longitude,
            rawDeviceId = deviceId,
            trustedDeviceId = activeTrusted.deviceId,
        )
    }

    fun auditRegistered(tenantId: UUID, userId: UUID, device: UserDevice) {
        audit.log(
            tenantId = tenantId,
            actorUserId = userId,
            action = "DEVICE_REGISTERED",
            resourceType = "UserDevice",
            resourceId = device.id?.toString(),
            metadata = mapOf(
                "userId" to userId.toString(),
                "deviceId" to device.deviceId,
                "platform" to device.platform,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun listDevices(tenantId: UUID, userId: UUID): List<DeviceView> {
        requireTenantMember(tenantId, userId)
        return devices.findAllByTenantIdAndUserIdOrderByLastSeenAtDesc(tenantId, userId).map { it.toView() }
    }

    @Transactional
    fun resetDevices(tenantId: UUID, targetUserId: UUID, actorUserId: UUID): List<DeviceView> {
        requireTenantMember(tenantId, targetUserId)
        val activeTrusted = devices.findActiveTrusted(tenantId, targetUserId)
        val now = Instant.now()
        activeTrusted.forEach { device ->
            device.active = false
            device.updatedAt = now
        }
        val saved = devices.saveAll(activeTrusted)
        audit.log(
            tenantId = tenantId,
            actorUserId = actorUserId,
            action = "DEVICE_RESET",
            resourceType = "UserDevice",
            resourceId = targetUserId.toString(),
            metadata = mapOf(
                "targetUserId" to targetUserId.toString(),
                "resetCount" to saved.size,
            ),
        )
        return devices.findAllByTenantIdAndUserIdOrderByLastSeenAtDesc(tenantId, targetUserId).map { it.toView() }
    }

    private fun requireTenantMember(tenantId: UUID, userId: UUID) {
        memberships.findByTenantIdAndUserId(tenantId, userId)
            ?: throw DomainException.notFound("Membership", userId)
    }

    private fun normalizeDeviceId(value: String?): String? {
        val normalized = value?.trim()
        if (normalized.isNullOrEmpty() || normalized.length > MAX_DEVICE_ID_LENGTH) return null
        return normalized
    }

    private fun reject(
        tenantId: UUID,
        userId: UUID,
        type: AttendanceType,
        reason: AttemptReason,
        errorCode: ErrorCode,
        message: String,
        latitude: Double?,
        longitude: Double?,
        rawDeviceId: String?,
        trustedDeviceId: String? = null,
    ): Nothing {
        attemptLogger.log(
            tenantId = tenantId,
            userId = userId,
            type = type,
            reason = reason,
            latitude = latitude,
            longitude = longitude,
            deviceId = rawDeviceId?.trim(),
        )
        audit.log(
            tenantId = tenantId,
            actorUserId = userId,
            action = "DEVICE_MISMATCH",
            resourceType = "UserDevice",
            resourceId = userId.toString(),
            metadata = mapOf(
                "reason" to reason.name,
                "attendanceType" to type.name,
                "providedDeviceId" to rawDeviceId?.trim(),
                "trustedDeviceId" to trustedDeviceId,
            ),
        )
        throw DomainException(errorCode, message)
    }

    private fun String?.clean(maxLength: Int): String? {
        val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.take(maxLength)
    }

    companion object {
        const val DEVICE_MISMATCH_MESSAGE =
            "Perangkat ini belum terdaftar untuk absensi. Hubungi admin untuk reset perangkat."
        private const val MAX_DEVICE_ID_LENGTH = 120
        private const val MAX_DEVICE_NAME_LENGTH = 120
        private const val MAX_PLATFORM_LENGTH = 60
    }
}
