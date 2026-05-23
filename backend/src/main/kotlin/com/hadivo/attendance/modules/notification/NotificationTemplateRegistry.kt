package com.hadivo.attendance.modules.notification

import org.springframework.stereotype.Component

@Component
class NotificationTemplateRegistry {

    fun templateFor(eventType: NotificationEventType): NotificationTemplate =
        when (eventType) {
            NotificationEventType.CLOCK_IN_SUCCESS -> NotificationTemplate(
                title = "Clock-in berhasil",
                body = "Absensi masuk Anda berhasil tercatat.",
                channels = DEFAULT_CHANNELS,
            )

            NotificationEventType.CLOCK_OUT_SUCCESS -> NotificationTemplate(
                title = "Clock-out berhasil",
                body = "Absensi pulang Anda berhasil tercatat.",
                channels = DEFAULT_CHANNELS,
            )

            NotificationEventType.ATTENDANCE_OUT_OF_RADIUS -> NotificationTemplate(
                title = "Absensi gagal",
                body = "Anda berada di luar area absensi.",
                channels = DEFAULT_CHANNELS,
            )

            NotificationEventType.DEVICE_MISMATCH -> NotificationTemplate(
                title = "Perangkat tidak sesuai",
                body = "Absensi hanya bisa dilakukan dari perangkat terdaftar.",
                channels = DEFAULT_CHANNELS,
            )

            NotificationEventType.ATTENDANCE_FAILED_ATTEMPT -> NotificationTemplate(
                title = "Absensi gagal",
                body = "Absensi belum berhasil. Periksa kembali kondisi absensi Anda.",
                channels = DEFAULT_CHANNELS,
            )
        }

    private companion object {
        val DEFAULT_CHANNELS = listOf(
            NotificationChannel.IN_APP,
            NotificationChannel.EMAIL,
            NotificationChannel.PUSH,
        )
    }
}
