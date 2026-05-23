package com.hadivo.attendance.modules.device

import java.time.Instant

data class DeviceView(
    val deviceId: String,
    val deviceName: String?,
    val platform: String?,
    val trusted: Boolean,
    val active: Boolean,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

data class DeviceBindingCommand(
    val deviceId: String?,
    val deviceName: String?,
    val platform: String?,
)

data class DeviceBindingResult(
    val device: UserDevice,
    val registered: Boolean,
)

fun UserDevice.toView(): DeviceView = DeviceView(
    deviceId = deviceId,
    deviceName = deviceName,
    platform = platform,
    trusted = trusted,
    active = active,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
)
