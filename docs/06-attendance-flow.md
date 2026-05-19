# Attendance flow

## Clock-in

1. Auth filter mengisi `AuthPrincipal`. Controller memanggil `MembershipGuard.requireMember`.
2. `AttendanceService.clockIn`:
   - load `tenant_attendance_settings`,
   - hitung "hari ini" pada timezone tenant,
   - kalau sudah ada `clock_in_at` hari ini → log `DUPLICATE_CLOCK_IN`, 422,
   - cek geofence terhadap semua `tenant_locations` aktif → bila tidak match log `OUT_OF_RADIUS`, 422,
   - kalau `require_face_clock_in` → panggil `FaceVerifier`. Gagal → log `FACE_MISMATCH`, 422,
   - hitung `LATE` berdasarkan `work_start_time + late_threshold_minutes`,
   - kalau LATE tapi `allow_late_clock_in = false` → log `LATE_NOT_ALLOWED`, 422,
   - simpan `attendance_records` dengan koordinat, deviceId, locationId, status,
   - tulis `audit_logs` dan publish event `ClockInOccurred`.

Event `ClockInOccurred` dikirim ke RabbitMQ **setelah** transaksi commit (lihat `09-notification-flow.md`).

## Clock-out

1. Load settings.
2. Cari record hari ini:
   - tidak ada → log `NO_CLOCK_IN`, 422,
   - `clock_out_at` sudah terisi → log `ALREADY_CLOCKED_OUT`, 422.
3. Cek geofence:
   - di luar dan `allow_clock_out_outside_radius = false` → log `OUT_OF_RADIUS`, 422,
   - di luar tapi diizinkan → set `clock_out_outside_radius = true`, `clock_out_location_id = null`,
   - di dalam → set `clock_out_location_id`, `clock_out_outside_radius = false`.
4. Kalau `require_face_clock_out` → verifikasi wajah, gagal → log `FACE_MISMATCH`, 422.
5. Update record:
   - `clock_out_at`, `clock_out_latitude`, `clock_out_longitude`, `clock_out_device_id`,
   - `work_duration_minutes`,
   - status `COMPLETED` atau `EARLY_LEAVE` (kalau jam pulang sebelum `work_end_time`).
6. Audit + publish event `ClockOutOccurred`.

## Status final

- `ON_TIME` — clock-in di bawah ambang keterlambatan.
- `LATE` — clock-in di atas ambang keterlambatan.
- `COMPLETED` — sudah clock-out dan di atas atau sama dengan `work_end_time`.
- `EARLY_LEAVE` — sudah clock-out tapi sebelum `work_end_time`.
