# Hadivo Attendance System

Hadivo adalah sistem absensi multi-tenant berbasis lokasi untuk sekolah dan perusahaan. Project ini berisi backend Kotlin Spring Boot dan Web Dashboard Next.js untuk kebutuhan portfolio SaaS attendance system.

Repository ini berisi backend Fase 1 (MVP) dan web dashboard admin Fase 2. Folder `mobile/` masih placeholder dan akan dikerjakan di fase berikutnya.

## Stack

- Backend Kotlin + Spring Boot 3.3
- Web Dashboard Next.js + TypeScript + Tailwind CSS
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
- Event notification pipeline memakai RabbitMQ.
- Database migration memakai Flyway dan PostgreSQL.

## Struktur

```
hadivo-attendance-system/
├── backend/          aplikasi Kotlin
├── docker/           docker-compose untuk Postgres + RabbitMQ
├── docs/             dokumentasi internal (overview, flow, schema, ADR)
├── postman/          Postman collection untuk QA manual
├── mobile/           placeholder, akan diisi Flutter di fase berikutnya
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

Detail langkah-langkah ada di [`docs/10-local-development.md`](docs/10-local-development.md).

## Screenshots

Screenshot belum disertakan di repository. Setelah menjalankan backend dan web secara lokal, capture halaman berikut lalu simpan ke path yang sudah disiapkan:

| Area | Path |
| --- | --- |
| Login page | `docs/images/web-login.png` |
| Dashboard summary | `docs/images/web-dashboard.png` |
| Attendance table | `docs/images/web-attendance.png` |
| Attendance attempts audit | `docs/images/web-attempts.png` |
| Swagger UI | `docs/images/swagger.png` |

Setelah file gambar tersedia, bagian ini bisa diganti menjadi preview gambar langsung:

```md
![Hadivo web login](docs/images/web-login.png)
![Hadivo dashboard](docs/images/web-dashboard.png)
![Hadivo attendance](docs/images/web-attendance.png)
![Hadivo attempts audit](docs/images/web-attempts.png)
![Hadivo Swagger UI](docs/images/swagger.png)
```

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
- Reporting harian & bulanan (JSON)
- Audit log untuk operasi absensi
- Postman collection siap pakai
- Unit test Haversine + integration test clock-in memakai PostgreSQL dari Docker Compose
- Web dashboard admin tenant untuk login, summary, attendance, attempts, members, settings, locations, dan subscription

## Known limitation (Fase 1)

- Face verification masih demo — hanya cek panjang base64. Interface `FaceVerifier` sudah siap diganti.
- SUPER_ADMIN sudah ada di enum/seed, tapi endpoint lintas tenant belum diimplementasi.
- Tidak ada gateway notifikasi nyata (FCM/email/SMS). Hanya tabel `notifications`.
- Subscription dibuat manual, belum terintegrasi dengan payment gateway.
- Tidak ada WebSocket atau realtime push.
- Reporting hanya JSON, tidak ada export PDF/Excel.
- Device binding belum strict; `DEVICE_MISMATCH` masih reserved enum.

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
