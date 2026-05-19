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

<http://localhost:15672> — user/password `hadivo`/`hadivo`. Exchange `attendance.events` dan queue `attendance.notifications` dibuat otomatis oleh aplikasi saat startup.

## Postman

Import `postman/hadivo-attendance.postman_collection.json`. Variabel `baseUrl`, `tenantId`, `accessToken`, `refreshToken` sudah disiapkan. Endpoint `Login` punya test script yang otomatis set `accessToken` dan `refreshToken`.
