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

## Leave / permission overlay

Approved `leave_requests` ditampilkan sebagai overlay pada daily report dan export. Di v1.2.0 approved leave **tidak** mengubah atau membuat `attendance_records`. Karena itu:

- Daily report row akan menampilkan `leaveType` dan `leaveStatus` di samping status absensi existing.
- Bila user hanya punya approved leave dan tidak punya record absensi, daily report tetap memunculkan baris dengan `status = null` dan field leave terisi.
- Export CSV/Excel/PDF menambah kolom `Leave Type` dan `Leave Status`. Baris leave-only memiliki kolom status absensi kosong.

`ATTENDANCE_CORRECTION` di v1.2.0 disimpan sebagai approved request dan dimunculkan di report sebagai informasi correction approved. Mutasi `clock_in_at`/`clock_out_at` ke `attendance_records` tidak dilakukan karena model belum punya audit trail `correctionRequestId`/`correctedBy`/`correctedAt`. Lihat `docs/16-leave-permission.md` untuk detail limitation dan roadmap.

## Correction apply engine (v1.3.0)

Approve `ATTENDANCE_CORRECTION` di v1.3.0 menerapkan koreksi ke `attendance_records` di transaksi yang sama dengan approve. Mekanisme:

1. Reviewer hit `/leave-requests/{id}/approve` dan status berubah ke `APPROVED` di memori.
2. `CorrectionApplyService.apply(request, reviewerUserId)` dipanggil dalam transaksi yang sama.
3. Service cek apakah `attendance_correction_applies` sudah punya row untuk `leave_request_id` — bila ya, log `ATTENDANCE_CORRECTION_ALREADY_APPLIED` dan kembalikan row existing (idempotent).
4. Cari `attendance_records` pada `(tenantId, requesterUserId, startDate)`:
   - **Ada record**: snapshot `clockInAt/clockOutAt/status/workDurationMinutes` lama → simpan ke `attendance_correction_applies` → update `clockInAt`/`clockOutAt` dari `requestedClockInAt`/`requestedClockOutAt` (hanya yang non-null) → recompute `status` + `workDurationMinutes` → set `correction_applied=true`, `correction_request_id`, `corrected_by`, `corrected_at`, `correction_note`. Lat/long/device/location_id/face/attendance_attempts TIDAK disentuh.
   - **Tidak ada record**: buat record baru dengan `clockInAt`/`clockOutAt` dari request, snapshot shift schedule, dan field correction. Lat/long/device/location_id semua `null` untuk menandai bukan absensi mobile asli. `record_created_by_correction=true` di apply audit row.
5. Audit `ATTENDANCE_CORRECTION_APPLIED` ditulis dengan metadata: `leaveRequestId`, `attendanceRecordId`, `originalClockInExists`, `originalClockOutExists`, `appliedClockInProvided`, `appliedClockOutProvided`, `originalStatus`, `appliedStatus`, `recordCreatedByCorrection`.
6. Audit `LEAVE_REQUEST_APPROVED` dan `ATTENDANCE_CORRECTION_APPROVED` tetap ditulis (semantik berbeda: APPROVED = keputusan reviewer; APPLIED = perubahan benar-benar diterapkan ke data absensi).
7. Bila apply gagal, audit `ATTENDANCE_CORRECTION_APPLY_FAILED` ditulis lewat `REQUIRES_NEW` (commit terlepas), exception dilempar, transaksi parent rollback → leave request tetap `PENDING`.

Status recomputation dilakukan oleh `AttendanceStatusCalculator` berbasis `ShiftScheduleResolver` (mengikuti shift assignment atau fallback `tenant_attendance_settings`). Aturan: clock-in only → `LATE`/`ON_TIME`; clock-in + clock-out → `EARLY_LEAVE`/`COMPLETED`.
