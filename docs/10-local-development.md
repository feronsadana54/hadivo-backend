# Local development

## Prasyarat

- JDK 21 (Temurin atau Liberica).
- Docker + Docker Compose.
- (Opsional) IntelliJ IDEA Community / Ultimate dengan plugin Kotlin.

## Setup backend

```bash
cp .env.example .env
docker compose -f docker/docker-compose.yml up -d
cd backend
.\gradlew.bat bootRun
```

Backend listen di `http://localhost:8080`.

Untuk validasi test backend:

```bash
cd backend
.\gradlew.bat clean test
```

## Setup web dashboard

Backend harus berjalan sebelum dashboard dipakai.

```bash
cd web
npm install
cp .env.example .env.local
npm run dev
```

Web dashboard listen di `http://localhost:3000`.

## Default credentials

Saat startup pertama, `DataSeeder` membuat user SUPER_ADMIN sesuai konfigurasi:

```
email    : superadmin@hadivo.local
password : ChangeMe123!
```

Ganti password segera setelah environment dikenal stabil. Override via env:

```
SEED_SUPER_ADMIN_EMAIL=...
SEED_SUPER_ADMIN_PASSWORD=...
```

## Demo tenant

`V2__seed_demo_tenant.sql` membuat:

- Tenant `Hadivo Demo School` (id `11111111-1111-1111-1111-111111111111`).
- Lokasi "Kampus Utama" di sekitar Monas (lat -6.2, lon 106.816666, radius 100 m).
- Subscription FREE aktif.
- Default attendance settings.

SUPER_ADMIN otomatis ditautkan ke tenant ini lewat `DataSeeder`.

## Swagger UI

<http://localhost:8080/swagger-ui.html>. Klik "Authorize" → masukkan `Bearer <accessToken>` dari hasil login.

## RabbitMQ UI

<http://localhost:15672> — user/password `hadivo`/`hadivo`. Exchange `attendance.events`, queue legacy `attendance.notifications`, dan queue notification gateway `hadivo.notification.events` dibuat otomatis oleh aplikasi saat startup.

## Optional notification providers

Default local development memakai mock/log-only email dan push provider. Untuk mencoba provider real, set env berikut secara lokal dan jangan commit nilainya:

```
HADIVO_NOTIFICATION_EMAIL_PROVIDER=resend
RESEND_API_KEY=...
RESEND_FROM_EMAIL=no-reply@domain-kamu.com

HADIVO_NOTIFICATION_PUSH_PROVIDER=fcm
FCM_ENABLED=true
FCM_PROJECT_ID=...
FCM_SERVICE_ACCOUNT_PATH=/absolute/path/to/firebase-service-account.json
```

Jika salah satu nilai Resend/FCM belum lengkap, aplikasi tetap berjalan dengan mock provider.

## Optional payment provider

Default local development memakai payment provider `mock`, sehingga backend, web, dan CI tidak membutuhkan Midtrans key.

Untuk mencoba Midtrans Snap sandbox, set env berikut secara lokal dan jangan commit nilainya:

```
HADIVO_PAYMENT_PROVIDER=midtrans
HADIVO_PAYMENT_MIDTRANS_ENABLED=true
HADIVO_PAYMENT_MIDTRANS_ENVIRONMENT=sandbox
MIDTRANS_SERVER_KEY=...
MIDTRANS_CLIENT_KEY=...
MIDTRANS_SNAP_BASE_URL=https://app.sandbox.midtrans.com
MIDTRANS_API_BASE_URL=https://api.sandbox.midtrans.com
```

Webhook URL untuk Midtrans:

```
POST http://localhost:8080/api/v1/payments/webhooks/midtrans
```

Jika provider tetap `mock`, halaman Subscription dapat membuat payment request lokal dan membuka dummy payment URL. Subscription tidak aktif dari frontend; status subscription berubah hanya setelah backend menerima webhook valid atau flow mock/test backend yang aman.

Jika `HADIVO_PAYMENT_PROVIDER=midtrans` tetapi `HADIVO_PAYMENT_MIDTRANS_ENABLED` belum `true` atau `MIDTRANS_SERVER_KEY` kosong, backend tetap memakai mock provider agar aplikasi tidak gagal start.

## Postman

Import `postman/hadivo-attendance.postman_collection.json`. Variabel `baseUrl`, `tenantId`, `accessToken`, `refreshToken` sudah disiapkan. Endpoint `Login` punya test script yang otomatis set `accessToken` dan `refreshToken`.
