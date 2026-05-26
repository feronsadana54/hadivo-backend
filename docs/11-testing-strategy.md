# Testing strategy

## Tujuan

Cek dua hal yang paling berisiko di Fase 1:

1. Perhitungan jarak benar — kalau Haversine salah, semua geofence salah.
2. Flow clock-in end-to-end menggunakan DB asli — kalau migrasi atau mapping JPA salah, baru ketahuan saat runtime.

## Unit test — `GeoUtilsTest`

Tanpa Spring. Beberapa kasus:

- Jarak titik ke dirinya sendiri = 0.
- Jakarta–Bandung di kisaran 110–130 km.
- Offset 0.001° pada lintang ≈ 111 m (toleransi ±5 m).
- `isWithinRadius` di pusat, pinggir, dan di luar.

## Integration test — `ClockInIntegrationTest`

`@SpringBootTest` memakai PostgreSQL lokal dari `docker compose -f docker/docker-compose.yml up -d`. Migrasi Flyway dijalankan, lalu test seed tenant + lokasi + user via repository.

Kasus yang ditest:

- Clock-in di dalam radius → response 201, record tersimpan dengan device id.
- Clock-in di luar radius → 422 dengan code `OUT_OF_RADIUS`, attempt tercatat di `attendance_attempts`.

RabbitTemplate di-mock pakai `@MockitoBean` agar test tidak butuh broker hidup. Listener queue `attendance.notifications` dimatikan via `spring.rabbitmq.listener.simple.auto-startup: false` di `application-test.yml`.

## Integration test — `NotificationGatewayIntegrationTest`

Test notification gateway v0.7.0 mengecek:

- clock-in success menghasilkan `NotificationRequest` dan delivery log `SENT`;
- device mismatch menghasilkan notification event/log;
- endpoint delivery log dapat diakses `TENANT_ADMIN` dan `SUPER_ADMIN` sesuai guard tenant;
- `EMPLOYEE` dan `STUDENT` ditolak 403 untuk delivery log tenant;
- kegagalan publish RabbitMQ tidak menggagalkan attendance flow;
- kegagalan mock gateway dicatat sebagai delivery `FAILED`.
- token registration v0.8.0 hanya memakai authenticated principal;
- non-member ditolak saat register notification token;
- push delivery menjadi `SKIPPED` jika user belum punya FCM token;
- delivery log tidak menyimpan full FCM token.

RabbitTemplate tetap di-mock agar test tidak membutuhkan broker aktif untuk memverifikasi publish dan best-effort behavior.

## Integration test — `SecurityHardeningIntegrationTest`

Test security hardening mengecek baseline v0.4.0:

- cross-tenant membership access ditolak;
- failed login lockout tidak membocorkan apakah email terdaftar;
- login sukses mereset failed counter;
- password policy diterapkan saat register;
- refresh token lama ditolak setelah rotation dan token aktif dicabut saat logout;
- security headers dasar ada di response backend;
- update attendance settings dan CSV export tercatat di audit log.

## Integration test — `AttendanceCsvExportIntegrationTest`

Test export attendance mengecek:

- CSV export mengembalikan attachment dengan header dan data tenant yang benar;
- XLSX export mengembalikan content type Excel, content disposition, body workbook tidak kosong, dan audit `REPORT_EXCEL_EXPORTED`;
- PDF export tetap sukses saat data kosong, mengembalikan content type PDF, content disposition, body tidak kosong, dan audit `REPORT_PDF_EXPORTED`;
- date range invalid ditolak dengan `VALIDATION_FAILED`;
- range lebih dari 31 hari ditolak;
- cross-tenant access untuk export ditolak.

## Integration test — payment foundation

Test payment v1.0.0 mengecek:

- `TENANT_ADMIN` dapat membuat mock payment tanpa Midtrans secret;
- `EMPLOYEE`, `STUDENT`, dan `PARENT` tidak boleh membuat payment tenant;
- cross-tenant create/list/detail payment ditolak;
- mock payment mengembalikan status `PENDING` dan `paymentUrl`;
- webhook Midtrans `PAID` mengaktifkan subscription dari backend;
- duplicate webhook `PAID` idempotent dan tidak membuat subscription dobel;
- webhook failed/expired tidak mengaktifkan subscription;
- webhook terlambat tidak dapat menurunkan status `PAID`;
- invalid signature dan amount mismatch ditolak;
- raw webhook JSON tidak diekspos di list/detail response;
- audit log dibuat untuk create, webhook received, status update, subscription activated, dan ignored webhook.

## Cara menjalankan

```bash
docker compose -f docker/docker-compose.yml up -d
cd backend
.\gradlew.bat clean test
```

Integration test memerlukan Docker daemon hidup dan service `hadivo-postgres` berjalan dari Docker Compose.

## Yang belum dicakup di Fase 1

- Test untuk clock-out dan attempt flow lain.
- Test untuk subscription limit.
- Test lebih dalam untuk parent fan-out dan preference notification.
- Test kontrak API (mis. Pact / OpenAPI conformance).

Ini masuk backlog Fase 2.
