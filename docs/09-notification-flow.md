# Notification flow

## Tujuan utama

Notification Gateway Foundation v0.7.0 menyiapkan jalur notifikasi yang rapi tanpa mengirim FCM atau email sungguhan. Semua delivery dicatat agar mudah diaudit, sementara provider `EMAIL` dan `PUSH` masih mock/log-only.

Notification failure tidak boleh menggagalkan attendance mutation. Publish ke RabbitMQ dilakukan setelah transaksi utama commit supaya tidak ada notifikasi untuk absensi yang rollback.

## Pola

```
AttendanceService (transactional)
   |
   |  publisher.publishEvent(ClockInOccurred / ClockOutOccurred / AttemptFailed)
   v
Spring application event
   |
   |  @TransactionalEventListener(phase = AFTER_COMMIT)
   v
AttendanceRabbitPublisher
   |
   |  NotificationPublisher.publish(NotificationRequest)
   v
RabbitMQ exchange "attendance.events"
   |
   |  routing key: notification.event
   v
Queue "hadivo.notification.events"
   |
   |  @RabbitListener
   v
NotificationConsumer
   |
   v
NotificationService
   |
   +--> IN_APP  -> notifications table + delivery log
   +--> EMAIL   -> MockEmailNotificationGateway + delivery log
   +--> PUSH    -> MockPushNotificationGateway + delivery log
```

## Event yang didukung

- `CLOCK_IN_SUCCESS`
- `CLOCK_OUT_SUCCESS`
- `ATTENDANCE_OUT_OF_RADIUS`
- `DEVICE_MISMATCH`
- `ATTENDANCE_FAILED_ATTEMPT`

`AttemptFailed` dengan reason `OUT_OF_RADIUS` dipetakan ke `ATTENDANCE_OUT_OF_RADIUS`. Reason `DEVICE_MISMATCH` dipetakan ke `DEVICE_MISMATCH`. Reason lain dipetakan ke `ATTENDANCE_FAILED_ATTEMPT`.

## Channel

- `IN_APP`: menulis ke tabel `notifications`.
- `EMAIL`: mock/log-only provider `mock-email`.
- `PUSH`: mock/log-only provider `mock-push`.

Fase ini belum memakai FCM, Resend, SMTP, SMS, API key provider, device token, atau mobile push token registration.

## Delivery log

Semua percobaan delivery dicatat di `notification_delivery_logs` dengan status:

- `PENDING`
- `SENT`
- `FAILED`
- `SKIPPED`

Delivery log menyimpan event type, channel, recipient user, destination, title, body, provider, provider message id mock jika ada, error message jika gagal, metadata aman, `created_at`, dan `sent_at`.

Metadata tidak boleh menyimpan password, access token, refresh token, JWT, API key, secret, atau credential provider.

## Access

Endpoint read-only:

`GET /api/v1/tenants/{tenantId}/notification-deliveries`

Admin tenant dapat melihat delivery log tenant-nya. `SUPER_ADMIN` mengikuti guard tenant yang berlaku. User biasa seperti `EMPLOYEE`, `STUDENT`, dan `PARENT` tidak boleh melihat seluruh delivery log tenant.

## Robustness

- Publish RabbitMQ dibungkus try/catch. Jika broker bermasalah, attendance flow tetap selesai.
- Consumer juga menangkap exception agar satu pesan gagal tidak menjatuhkan proses aplikasi.
- Gateway failure dicatat sebagai delivery `FAILED`.
- Retry scheduler dan outbox durable belum dibuat di fase ini.
