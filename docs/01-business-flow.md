# Business flow

## Onboarding tenant

1. Sales / TENANT_ADMIN registrasi user lewat `/api/v1/auth/register`.
2. User membuat tenant via `POST /api/v1/tenants`. Backend otomatis:
   - menambahkan user sebagai `TENANT_ADMIN`,
   - membuat `tenant_attendance_settings` default,
   - membuat subscription `FREE` aktif.
3. TENANT_ADMIN menambahkan lokasi via `POST .../locations`.
4. TENANT_ADMIN menambahkan anggota via `POST .../memberships`.
5. (SCHOOL) TENANT_ADMIN menautkan parent ke student via `POST .../parent-links`.

## Absensi harian

Per user, satu hari satu record di `attendance_records` (UNIQUE `tenant_id`, `user_id`, `date`).

1. User membuka app dan trigger clock-in.
2. App mengirim `{ latitude, longitude, deviceId, faceImageBase64? }`.
3. Backend memvalidasi geofence, (opsional) wajah, ambang keterlambatan.
4. Bila gagal, percobaan dicatat di `attendance_attempts`. Bila sukses, record dibuat / diperbarui.
5. Event dipublish ke RabbitMQ **setelah commit**. Notification listener fan-out ke recipient (user + parent kalau STUDENT).

Clock-out mirip clock-in tapi memperbarui record yang sama dan menghitung durasi kerja.

## Upgrade subscription

Subscription baru dibuat lewat `POST .../subscriptions`. Subscription aktif sebelumnya otomatis di-`CANCELLED` agar selalu satu yang aktif per tenant.
