# Notification flow

## Tujuan utama

Notification Gateway memproses event absensi secara async, mencatat delivery log, dan menjaga attendance flow tetap best effort. Notification failure tidak boleh menggagalkan clock-in atau clock-out.

Default provider tetap mock/log-only. v0.8.0 menambahkan optional provider Resend untuk email dan Firebase Cloud Messaging untuk push notification. Jika env/config provider real belum lengkap, aplikasi tetap berjalan memakai mock provider.

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
   +--> EMAIL   -> MockEmailNotificationGateway or ResendEmailNotificationGateway
   +--> PUSH    -> MockPushNotificationGateway or FcmPushNotificationGateway
```

## Provider config

Mock provider default:

```
HADIVO_NOTIFICATION_EMAIL_PROVIDER=mock
HADIVO_NOTIFICATION_PUSH_PROVIDER=mock
FCM_ENABLED=false
```

Resend:

```
HADIVO_NOTIFICATION_EMAIL_PROVIDER=resend
RESEND_API_KEY=...
RESEND_FROM_EMAIL=no-reply@domain-kamu.com
```

FCM backend:

```
HADIVO_NOTIFICATION_PUSH_PROVIDER=fcm
FCM_ENABLED=true
FCM_PROJECT_ID=...
FCM_SERVICE_ACCOUNT_PATH=/absolute/path/to/firebase-service-account.json
```

Jangan commit API key, Firebase service account JSON, `google-services.json`, `GoogleService-Info.plist`, FCM token, atau file credential lain.

## Event yang didukung

- `CLOCK_IN_SUCCESS`
- `CLOCK_OUT_SUCCESS`
- `ATTENDANCE_OUT_OF_RADIUS`
- `DEVICE_MISMATCH`
- `ATTENDANCE_FAILED_ATTEMPT`

`AttemptFailed` dengan reason `OUT_OF_RADIUS` dipetakan ke `ATTENDANCE_OUT_OF_RADIUS`. Reason `DEVICE_MISMATCH` dipetakan ke `DEVICE_MISMATCH`. Reason lain dipetakan ke `ATTENDANCE_FAILED_ATTEMPT`.

## Channel

- `IN_APP`: menulis ke tabel `notifications`.
- `EMAIL`: mengirim ke email user jika ada. Provider default `mock-email`; provider real optional `resend`.
- `PUSH`: mengirim ke active FCM token user jika ada. Provider default `mock-push`; provider real optional `fcm`.

Jika tidak ada email atau token aktif, delivery log berstatus `SKIPPED`.

## Delivery log

Semua percobaan delivery dicatat di `notification_delivery_logs` dengan status:

- `PENDING`
- `SENT`
- `FAILED`
- `SKIPPED`

Delivery log menyimpan event type, channel, recipient user, masked destination, title, body, provider, provider message id jika ada, error message aman jika gagal, metadata aman, `created_at`, dan `sent_at`.

Metadata dan destination tidak boleh menyimpan password, access token, refresh token, JWT, API key, secret, credential provider, atau full FCM token.

## Token registration

Mobile app dapat mendaftarkan FCM token melalui:

`POST /api/v1/tenants/{tenantId}/notification-tokens`

Backend selalu memakai user dari access token. Request tidak boleh menentukan user target. Non-member tenant mendapat 403.

Mobile Firebase Messaging aktif hanya jika app dijalankan dengan:

`--dart-define=HADIVO_ENABLE_FIREBASE_MESSAGING=true`

Jika Firebase belum dikonfigurasi, mobile app tetap bisa dipakai tanpa push token registration.

## Access

Endpoint read-only delivery log:

`GET /api/v1/tenants/{tenantId}/notification-deliveries`

Admin tenant dapat melihat delivery log tenant-nya. `SUPER_ADMIN` mengikuti guard tenant yang berlaku. User biasa seperti `EMPLOYEE`, `STUDENT`, dan `PARENT` tidak boleh melihat seluruh delivery log tenant.

## Robustness

- Publish RabbitMQ dibungkus try/catch. Jika broker bermasalah, attendance flow tetap selesai.
- Consumer menangkap exception agar satu pesan gagal tidak menjatuhkan proses aplikasi.
- Gateway failure dicatat sebagai delivery `FAILED`.
- Missing email/token dicatat sebagai delivery `SKIPPED`.
- Retry scheduler dan outbox durable belum dibuat di fase ini.
