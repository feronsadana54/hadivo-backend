# Hadivo Attendance System

Backend untuk sistem absensi multi-tenant berbasis lokasi dan (opsional) verifikasi wajah. Mendukung dua mode tenant: sekolah dan perusahaan.

Repository ini berisi backend Fase 1 (MVP). Folder `mobile/` dan `web/` masih placeholder dan akan dikerjakan di fase berikutnya.

## Stack

- Kotlin + Spring Boot 3.3
- Java 21
- PostgreSQL 16 + Flyway
- RabbitMQ 3 (notification event)
- Gradle Kotlin DSL
- JWT (access + refresh) dengan refresh token disimpan sebagai SHA-256 hash
- springdoc-openapi untuk Swagger UI

## Struktur

```
hadivo-attendance-system/
├── backend/          aplikasi Kotlin
├── docker/           docker-compose untuk Postgres + RabbitMQ
├── docs/             dokumentasi internal (overview, flow, schema, ADR)
├── postman/          Postman collection untuk QA manual
├── mobile/           placeholder, akan diisi Flutter di fase berikutnya
└── web/              placeholder, akan diisi web admin di fase berikutnya
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

Detail langkah-langkah ada di [`docs/10-local-development.md`](docs/10-local-development.md).

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
