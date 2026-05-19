# Database design

PostgreSQL 16. Semua ID pakai UUID v4. Semua tabel utama punya `created_at` + `updated_at`. Pengecualian:

- `audit_logs` — append-only, hanya `created_at`.
- `refresh_tokens` — sesuai spec eksplisit (id, user_id, token_hash, expires_at, revoked_at, created_at).

## Tabel

| Tabel | Catatan |
| --- | --- |
| `tenants` | Identitas tenant. `mode` enum: SCHOOL / COMPANY. |
| `users` | User global. Satu user bisa join banyak tenant. |
| `memberships` | (tenant_id, user_id, role). UNIQUE per (tenant_id, user_id). |
| `parent_student_links` | Relasi PARENT ↔ STUDENT terpisah dari `memberships`. |
| `subscriptions` | Plan + `max_members` + `status`. |
| `tenant_locations` | Lokasi geofence per tenant (lat, lon, radius_meters). |
| `tenant_attendance_settings` | Setting absensi per tenant (jam kerja, late threshold, face requirement, dll). |
| `attendance_records` | **Hanya absensi sah**. UNIQUE (tenant_id, user_id, date). Menyimpan koordinat & device_id untuk clock-in dan clock-out. |
| `attendance_attempts` | Percobaan gagal (OUT_OF_RADIUS, FACE_MISMATCH, DUPLICATE_CLOCK_IN, dst). |
| `notifications` | Notifikasi per user (payload jsonb). |
| `audit_logs` | Append-only audit. |
| `refresh_tokens` | `token_hash` SHA-256, bukan plaintext. |

## Index notable

- `attendance_records (tenant_id, date)` dan `(user_id, date)` untuk filter cepat.
- `attendance_attempts (tenant_id, user_id)` dan `(created_at)`.
- `notifications (recipient_user_id) WHERE read_at IS NULL` partial index.
- `audit_logs (tenant_id, created_at DESC)`.

## Migrasi

Flyway. File di `backend/src/main/resources/db/migration/`:

- `V1__init_schema.sql` — semua tabel di atas.
- `V2__seed_demo_tenant.sql` — tenant demo + lokasi + settings + subscription. User SUPER_ADMIN dibuat lewat `DataSeeder` (BCrypt-encoded password dari konfigurasi).
