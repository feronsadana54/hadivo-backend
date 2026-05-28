# Attendance flow

## Clock-in

1. Auth filter mengisi `AuthPrincipal`. Controller memanggil `MembershipGuard.requireMember`.
2. `AttendanceService.clockIn`:
   - load `tenant_attendance_settings`,
   - resolve jadwal harian user dari assignment shift aktif; jika tidak ada, fallback ke attendance settings tenant,
   - hitung attendance date pada timezone tenant atau tanggal shift untuk overnight shift,
   - validasi device binding: device pertama auto-register, device berbeda → log `DEVICE_MISMATCH`, 422, device kosong/tidak valid → log `INVALID_DEVICE`, 422,
   - kalau sudah ada `clock_in_at` hari ini → log `DUPLICATE_CLOCK_IN`, 422,
   - cek geofence terhadap semua `tenant_locations` aktif → bila tidak match log `OUT_OF_RADIUS`, 422,
   - kalau `require_face_clock_in` → panggil `FaceVerifier`. Gagal → log `FACE_MISMATCH`, 422,
   - hitung `LATE` berdasarkan scheduled start time + late threshold dari shift atau fallback settings,
   - kalau LATE tapi `allow_late_clock_in = false` → log `LATE_NOT_ALLOWED`, 422,
   - simpan `attendance_records` dengan koordinat, deviceId, locationId, status, dan snapshot shift schedule,
   - tulis `audit_logs` dan publish event `ClockInOccurred`.

Event `ClockInOccurred` dikirim ke RabbitMQ **setelah** transaksi commit (lihat `09-notification-flow.md`).

## Clock-out

1. Load settings dan resolve jadwal harian user.
2. Validasi device binding:
   - device pertama auto-register bila belum ada active trusted device,
   - device yang sama diperbolehkan dan memperbarui `last_seen_at`,
   - device berbeda → log `DEVICE_MISMATCH`, 422,
   - device kosong/tidak valid → log `INVALID_DEVICE`, 422.
3. Cari record berdasarkan attendance date hasil resolver:
   - tidak ada → log `NO_CLOCK_IN`, 422,
   - `clock_out_at` sudah terisi → log `ALREADY_CLOCKED_OUT`, 422.
4. Cek geofence:
   - di luar dan `allow_clock_out_outside_radius = false` → log `OUT_OF_RADIUS`, 422,
   - di luar tapi diizinkan → set `clock_out_outside_radius = true`, `clock_out_location_id = null`,
   - di dalam → set `clock_out_location_id`, `clock_out_outside_radius = false`.
5. Kalau `require_face_clock_out` → verifikasi wajah, gagal → log `FACE_MISMATCH`, 422.
6. Update record:
   - `clock_out_at`, `clock_out_latitude`, `clock_out_longitude`, `clock_out_device_id`,
   - `work_duration_minutes`,
   - status `COMPLETED` atau `EARLY_LEAVE` (kalau jam pulang sebelum scheduled end time).
7. Audit + publish event `ClockOutOccurred`.

## Device binding

Satu user dalam satu tenant memiliki satu active trusted attendance device. Device pertama yang berhasil melewati policy device akan disimpan di `user_devices`. Admin tenant dapat reset device user dari endpoint admin agar device berikutnya bisa auto-register.

Device ID mobile dibuat sebagai random UUID dan disimpan di secure storage. Hadivo tidak memakai hardware identifier mentah untuk policy MVP ini.

## Status final

- `ON_TIME` — clock-in di bawah ambang keterlambatan.
- `LATE` — clock-in di atas ambang keterlambatan.
- `COMPLETED` — sudah clock-out dan di atas atau sama dengan `work_end_time`.
- `EARLY_LEAVE` — sudah clock-out tapi sebelum `work_end_time`.
