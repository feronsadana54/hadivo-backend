# Hadivo Attendance System

[![Backend CI](https://github.com/feronsadana54/hadivo-backend/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/feronsadana54/hadivo-backend/actions/workflows/backend-ci.yml)
[![Web CI](https://github.com/feronsadana54/hadivo-backend/actions/workflows/web-ci.yml/badge.svg)](https://github.com/feronsadana54/hadivo-backend/actions/workflows/web-ci.yml)
[![Mobile CI](https://github.com/feronsadana54/hadivo-backend/actions/workflows/mobile-ci.yml/badge.svg)](https://github.com/feronsadana54/hadivo-backend/actions/workflows/mobile-ci.yml)

Hadivo adalah sistem absensi multi-tenant berbasis lokasi untuk sekolah dan perusahaan. Project ini berisi backend Kotlin Spring Boot, Web Dashboard Next.js, dan Flutter Mobile Attendance App MVP untuk kebutuhan portfolio SaaS attendance system.

Repository ini berisi backend Fase 1 (MVP), web dashboard admin Fase 2, dan mobile app MVP untuk user attendance.

## Stack

- Backend Kotlin + Spring Boot 3.3
- Web Dashboard Next.js + TypeScript + Tailwind CSS
- Mobile Flutter + Dart
- Java 21
- PostgreSQL 16 + Flyway
- RabbitMQ 3 (notification event)
- Gradle Kotlin DSL
- JWT (access + refresh) dengan refresh token disimpan sebagai SHA-256 hash
- springdoc-openapi untuk Swagger UI

## Highlight

- Geolocation attendance validation dengan Haversine radius check.
- Attendance attempts audit untuk percobaan gagal seperti `OUT_OF_RADIUS`, `FACE_MISMATCH`, dan `DUPLICATE_CLOCK_IN`.
- Role-based dashboard untuk admin tenant, manager, teacher, employee, student, dan parent.
- Web dashboard untuk login, summary, attendance, attempts, members, settings, locations, dan subscription.
- Halaman Locations web memakai map picker berbasis Leaflet + OpenStreetMap dengan address search Nominatim untuk memilih titik absensi dan melihat radius geofence.
- Flutter mobile MVP untuk login, attendance hari ini, clock-in, clock-out, history, profile, dan logout.
- UX web dan mobile memakai label sederhana, status badge, empty state, dan pesan error yang lebih mudah dipahami user awam.
- Event notification pipeline memakai RabbitMQ.
- Database migration memakai Flyway dan PostgreSQL.
- Security baseline mencakup tenant isolation, role guard, audit log, login protection, refresh token rotation, dan security headers dasar.

## Struktur

```
hadivo-attendance-system/
├── backend/          aplikasi Kotlin
├── docker/           docker-compose untuk Postgres + RabbitMQ
├── docs/             dokumentasi internal (overview, flow, schema, ADR)
├── postman/          Postman collection untuk QA manual
├── mobile/           Flutter mobile attendance MVP
└── web/              dashboard admin tenant
```

## Menjalankan lokal

1. Pastikan Docker dan JDK 21 terpasang.
2. `cp .env.example .env` lalu sesuaikan kalau perlu.
3. Naikkan infrastruktur:
   ```
   docker compose -f docker/docker-compose.yml up -d
   ```
4. Jalankan test dan backend:
   ```
   cd backend
   .\gradlew.bat clean test
   .\gradlew.bat bootRun
   ```
5. Swagger UI: <http://localhost:8080/swagger-ui.html>
6. Login pakai user seed `superadmin@hadivo.local` / `ChangeMe123!` (ganti password segera).

## Menjalankan web dashboard

Backend harus berjalan di <http://localhost:8080>.

```
cd web
npm install
cp .env.example .env.local
npm run dev
```

Web dashboard tersedia di <http://localhost:3000>. Login default: `superadmin@hadivo.local` / `ChangeMe123!`.

Halaman Locations menyediakan map picker berbasis Leaflet + OpenStreetMap. Admin dapat klik peta untuk mengisi latitude/longitude, melihat marker, dan melihat circle radius sebelum menyimpan. Admin juga dapat mencari alamat atau nama tempat memakai Nominatim OpenStreetMap lewat tombol Cari Lokasi atau tombol Enter; fitur ini bukan live autocomplete. Fitur ini tidak membutuhkan Google Maps API key, billing, atau akun pihak ketiga. Untuk traffic production yang besar, gunakan tile/geocoding provider resmi/berbayar atau self-hosted tile/Nominatim yang sesuai dengan policy OpenStreetMap.

Detail langkah-langkah ada di [`docs/10-local-development.md`](docs/10-local-development.md).

## Mobile App

Flutter Mobile Attendance App MVP tersedia di folder [`mobile/`](mobile). App ini ditujukan untuk user attendance seperti employee/student, bukan dashboard admin.

Fitur mobile saat ini:

- Login.
- Melihat status attendance hari ini.
- Clock-in.
- Clock-out.
- Attendance history.
- Profile sederhana.
- Logout.

Backend lokal harus berjalan di port 8080 sebelum app dipakai untuk login dan absensi.

```
cd mobile
flutter pub get
flutter run
```

Default API base URL mobile adalah `http://10.0.2.2:8080` untuk Android emulator. Untuk target web/local biasa gunakan:

```
flutter run --dart-define=HADIVO_API_BASE_URL=http://localhost:8080
```

Login demo mobile:

- `employee@hadivo.local` / `ChangeMe123!`
- `student@hadivo.local` / `ChangeMe123!`

Mobile app memakai tenant demo `11111111-1111-1111-1111-111111111111`. Mode demo location aktif secara default agar clock-in/out memakai koordinat seed tenant (`-6.2`, `106.816666`) dan bisa berhasil tanpa mengatur GPS emulator manual. Detail ada di [`mobile/README.md`](mobile/README.md).

UI mobile dirancang sederhana untuk kebutuhan employee/student: user cukup login, melihat status hari ini, clock-in, clock-out, membuka riwayat, dan logout.

Screenshot mobile belum tersedia di repo karena belum ada capture emulator asli. Folder placeholder sudah disiapkan di `docs/images/mobile/`, dan panduan capture manual tersedia di [`mobile/README.md`](mobile/README.md).

## Continuous Integration

Repository ini memakai GitHub Actions untuk validasi setiap push dan pull request ke branch `main`.

- **Backend CI** menjalankan Java 21, Gradle cache, PostgreSQL 16, RabbitMQ 3, lalu `./gradlew clean test` dari folder `backend/`.
- **Web CI** menjalankan Node.js 20, `npm ci`, `npm run lint`, dan `npm run build` dari folder `web/`.
- **Mobile CI** menjalankan Flutter stable, `flutter pub get`, `flutter analyze`, dan `flutter test` dari folder `mobile/`.

CI memakai konfigurasi local/test dan tidak membutuhkan file `.env` asli atau secret production.

## Security Baseline

Hadivo memakai tenant isolation berbasis `tenantId` path dan membership guard untuk endpoint tenant-scoped. Refresh token disimpan sebagai hash, dirotasi saat refresh berhasil, dan dicabut saat logout.

Login protection MVP memakai in-memory failed login counter: 5 kali gagal dalam 15 menit akan mengunci login sementara selama 15 menit. Limitasi: production multi-instance sebaiknya memakai Redis atau rate limiter terpusat.

Audit log mencatat aksi penting seperti login, logout, refresh token, tenant changes, member changes, location changes, attendance settings update, attendance flow, subscription update, dan CSV export. Audit metadata tidak boleh menyimpan password, access token, refresh token, JWT, secret, atau data rahasia.

Detail baseline tersedia di [`docs/12-security-baseline.md`](docs/12-security-baseline.md).

## Release Notes

Release notes untuk `v0.4.0`, `v0.3.0`, `v0.2.0`, dan `v0.1.0` tersedia di [`CHANGELOG.md`](CHANGELOG.md). `v0.4.0` menambahkan security baseline untuk tenant isolation, login protection, audit coverage, refresh token tests, dan security headers tanpa perubahan schema backend atau UI frontend.

## Screenshots

Screenshot berikut diambil dari aplikasi web yang berjalan lokal.

![Hadivo web login](docs/images/web-login.png)
![Hadivo dashboard](docs/images/web-dashboard.png)
![Hadivo attendance](docs/images/web-attendance.png)
![Hadivo attempts audit](docs/images/web-attempts.png)
![Hadivo settings](docs/images/web-settings.png)
![Hadivo locations](docs/images/web-locations.png)
![Hadivo subscription](docs/images/web-subscription.png)

Responsive dashboard:

![Hadivo dashboard mobile](docs/images/responsive-dashboard-mobile.png)
![Hadivo dashboard tablet](docs/images/responsive-dashboard-tablet.png)
![Hadivo dashboard desktop](docs/images/responsive-dashboard-desktop.png)

Swagger and Postman screenshots can be added after manual capture.

## Fitur Fase 1

- Auth JWT (register, login, refresh, logout) + refresh token rotation
- Tenant, membership, parent-student link
- Subscription dengan empat plan (FREE 10, PRO 100, BUSINESS 500, ENTERPRISE unlimited)
- Tenant location + radius
- Tenant attendance settings yang dapat dikustomisasi (jam kerja, late threshold, face requirement, dll)
- Clock-in & clock-out dengan validasi geofence (Haversine) dan demo face verification
- `attendance_records` hanya menyimpan absensi sah; `attendance_attempts` mencatat percobaan gagal
- Event publish ke RabbitMQ **after commit** menggunakan `@TransactionalEventListener`
- Notifikasi disimpan di tabel `notifications`; untuk student, fan-out ke parent
- Reporting harian & bulanan (JSON) serta export CSV laporan attendance
- Audit log untuk operasi absensi
- Postman collection siap pakai
- Unit test Haversine + integration test clock-in memakai PostgreSQL dari Docker Compose
- Web dashboard admin tenant untuk login, summary, attendance, attempts, members, settings, locations, dan subscription
- Flutter mobile attendance MVP untuk employee/student demo

## Known limitation (Fase 1)

- Face verification masih demo — hanya cek panjang base64. Interface `FaceVerifier` sudah siap diganti.
- SUPER_ADMIN sudah ada di enum/seed, tapi endpoint lintas tenant belum diimplementasi.
- Tidak ada gateway notifikasi nyata (FCM/email/SMS). Hanya tabel `notifications`.
- Subscription dibuat manual, belum terintegrasi dengan payment gateway.
- Tidak ada WebSocket atau realtime push.
- Tidak ada export PDF/Excel untuk laporan attendance.
- Device binding belum strict; `DEVICE_MISMATCH` masih reserved enum.
- Address search Locations web memakai Nominatim OpenStreetMap untuk demo/portfolio dan request ringan, bukan live autocomplete.
- Tidak ada integrasi Google Maps.
- Tidak ada routing atau navigasi peta.
- Tile OpenStreetMap public dan public Nominatim sebaiknya diganti provider resmi/berbayar atau self-hosted tile/Nominatim untuk traffic production yang besar.
- Mobile app belum memiliki offline mode, push notification, map view, dan face recognition asli.

## Roadmap

| Fase | Lingkup |
| --- | --- |
| 2 | SUPER_ADMIN console + cross-tenant analytics |
| 2 | Real face recognition (ML / embedding) |
| 2 | Gateway notifikasi (FCM, email) |
| 2 | Payment gateway untuk subscription |
| 2 | Export laporan PDF/Excel |
| 2 | Device binding & multi-device policy |
| 3 | Mobile app (Flutter) |
| 3 | Web admin |
| 3 | Shift / jadwal fleksibel per user |

## Lisensi

Internal. Belum publik.
