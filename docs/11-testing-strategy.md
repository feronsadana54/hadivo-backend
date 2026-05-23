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
