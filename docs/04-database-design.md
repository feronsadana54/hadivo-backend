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
| `subscription_packages` | Catalog package berbayar untuk payment subscription. |
| `payment_records` | Request pembayaran tenant, provider order id, status, payment URL, dan webhook sanitized. |
| `shift_templates` | Template jadwal tenant untuk jam mulai, jam selesai, toleransi telat, dan status aktif. |
| `member_shift_assignments` | Assignment shift per anggota tenant dengan periode efektif. |
| `tenant_locations` | Lokasi geofence per tenant (lat, lon, radius_meters). |
| `tenant_attendance_settings` | Setting absensi per tenant (jam kerja, late threshold, face requirement, dll). |
| `attendance_records` | **Hanya absensi sah**. UNIQUE (tenant_id, user_id, date). Menyimpan koordinat & device_id untuk clock-in dan clock-out. |
| `attendance_attempts` | Percobaan gagal (OUT_OF_RADIUS, FACE_MISMATCH, DUPLICATE_CLOCK_IN, dst). |
| `leave_requests` | Pengajuan izin/sakit/cuti/dinas/koreksi absensi per tenant + user. Status `PENDING`/`APPROVED`/`REJECTED`/`CANCELLED`. Untuk `ATTENDANCE_CORRECTION` menyimpan `requested_clock_in_at` dan `requested_clock_out_at`. |
| `attendance_correction_applies` | Audit trail satu-per-request untuk apply ATTENDANCE_CORRECTION. UNIQUE per `leave_request_id` agar idempotent. Menyimpan original vs applied `clock_in_at`/`clock_out_at`/`status`/`work_duration_minutes`, reviewer, applied_by, applied_at, dan flag `record_created_by_correction`. |
| `attendance_records` (v1.3.0) | Tambahan kolom `correction_applied` (boolean), `correction_request_id`, `corrected_by`, `corrected_at`, `correction_note` untuk membedakan record hasil koreksi dari clock-in mobile asli. Geofence/device/face/attempt history TIDAK diubah saat apply. |
| `leave_policies` (v1.4.0) | Satu policy aktif per tenant (UNIQUE `tenant_id`). Berisi `annual_leave_quota_days` (default 12, 0..365) dan flag opsional `*_requires_balance` untuk SICK/PERMISSION/BUSINESS_TRIP (default false). |
| `leave_balances` (v1.4.0) | Saldo cuti per `(tenant_id, user_id, year)` UNIQUE. Kolom `annual_quota_days`, `used_days`, `adjusted_days`, `remaining_days` (`numeric(8,2)`). Dibuat lazy saat dibutuhkan. |
| `leave_balance_ledgers` (v1.4.0) | Riwayat tiap perubahan saldo (`INITIAL`, `DEDUCT`, `ADJUST`, `RESTORE`) dengan snapshot before/after. Partial UNIQUE index `(leave_request_id) WHERE change_type='DEDUCT'` untuk menjamin idempotency deduct per leave request. |
| `user_devices` | Trusted attendance device per tenant dan user. Satu active trusted device per (tenant_id, user_id). |
| `notifications` | Notifikasi per user (payload jsonb). |
| `notification_delivery_logs` | Delivery log notification gateway per tenant, channel, event type, status, provider, error, dan waktu kirim. |
| `notification_device_tokens` | Active FCM token per tenant, user, dan device untuk push notification. |
| `audit_logs` | Append-only audit. |
| `refresh_tokens` | `token_hash` SHA-256, bukan plaintext. |

## Index notable

- `attendance_records (tenant_id, date)` dan `(user_id, date)` untuk filter cepat.
- `attendance_attempts (tenant_id, user_id)` dan `(created_at)`.
- `user_devices (tenant_id, user_id)` untuk lookup trusted device.
- Partial unique index `user_devices (tenant_id, user_id) WHERE active = true AND trusted = true`.
- `notifications (recipient_user_id) WHERE read_at IS NULL` partial index.
- `notification_delivery_logs (tenant_id, created_at DESC)` dan `(tenant_id, event_type, channel, status)`.
- `notification_device_tokens (tenant_id, user_id)` dan partial active index untuk lookup token push.
- `payment_records (tenant_id, created_at DESC)`, `(tenant_id, status)`, dan unique `provider_order_id`.
- `shift_templates (tenant_id)`, `(tenant_id, active)`.
- `member_shift_assignments (tenant_id, user_id, active, effective_from, effective_to)` dan `(shift_template_id)`.
- `subscription_packages (active, plan, billing_period)` untuk pilihan paket aktif.
- `audit_logs (tenant_id, created_at DESC)`.

## Migrasi

Flyway. File di `backend/src/main/resources/db/migration/`:

- `V1__init_schema.sql` — semua tabel di atas.
- `V2__seed_demo_tenant.sql` — tenant demo + lokasi + settings + subscription. User SUPER_ADMIN dibuat lewat `DataSeeder` (BCrypt-encoded password dari konfigurasi).
- `V3__add_user_devices.sql` — tabel trusted attendance device untuk Device Binding v0.6.0.
- `V4__add_notification_delivery_logs.sql` — tabel delivery log untuk Notification Gateway Foundation v0.7.0.
- `V5__add_notification_device_tokens.sql` — tabel FCM token registration untuk provider push v0.8.0.
- `V6__add_subscription_payment_foundation.sql` — catalog package subscription dan payment record foundation untuk v1.0.0.
- `V7__add_shift_schedule_foundation.sql` — shift template, assignment anggota, dan snapshot shift di attendance record untuk v1.1.0.
