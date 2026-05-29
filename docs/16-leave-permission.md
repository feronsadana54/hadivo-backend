# Leave / permission request

Fitur leave/permission request (v1.2.0) memungkinkan user mengajukan sakit, izin, cuti sederhana, dinas luar, atau koreksi absensi, lalu admin tenant meninjau (approve / reject). User dapat membatalkan pengajuan miliknya selama statusnya `PENDING`.

## Tipe pengajuan

| `requestType` | Label web | Catatan |
| --- | --- | --- |
| `SICK` | Sakit | `reason` wajib. |
| `PERMISSION` | Izin | `reason` wajib. |
| `ANNUAL_LEAVE` | Cuti | `reason` wajib. Tidak ada saldo cuti / accrual di v1.2.0. |
| `BUSINESS_TRIP` | Dinas luar | `reason` wajib. |
| `ATTENDANCE_CORRECTION` | Koreksi absensi | Minimal salah satu dari `requestedClockInTime`/`requestedClockOutTime` wajib. |

## Status

`PENDING` → `APPROVED` (oleh reviewer) atau `REJECTED` (oleh reviewer) atau `CANCELLED` (oleh requester). Setelah keluar dari `PENDING`, status tidak bisa diubah kembali.

## Access policy

| Role | Self-create | List own | List all tenant | Approve/Reject | Cancel |
| --- | --- | --- | --- | --- | --- |
| `TENANT_ADMIN` | ✅ | ✅ | ✅ | ✅ | hanya milik sendiri |
| `SUPER_ADMIN` | ✅ | ✅ | ✅ | ✅ | hanya milik sendiri |
| `MANAGER` | ✅ | ✅ | ❌ | ❌ | hanya milik sendiri |
| `TEACHER` | ✅ | ✅ | ❌ | ❌ | hanya milik sendiri |
| `EMPLOYEE` | ✅ | ✅ | ❌ | ❌ | hanya milik sendiri |
| `STUDENT` | ✅ | ✅ | ❌ | ❌ | hanya milik sendiri |
| `PARENT` | ❌ (school flow belum dibuka) | — | — | — | — |

Cross-tenant access ditolak dengan 403.

## Approval behavior

- Approve: status `PENDING` → `APPROVED`, simpan `reviewerUserId`, `reviewedAt`, `reviewNote`.
- Reject: status `PENDING` → `REJECTED` dengan field yang sama.
- Cancel: requester saja, hanya saat `PENDING`.
- Setiap transisi mencatat audit dan men-publish event notifikasi.

### ATTENDANCE_CORRECTION apply (v1.3.0+)

Mulai v1.3.0, approve correction **menerapkan** perubahan ke `attendance_records` dengan audit trail penuh. Untuk panduan QA manual dan diskusi revert planning lihat [`docs/18-correction-qa-guide.md`](18-correction-qa-guide.md).

- Status `APPROVED` berarti koreksi sudah berhasil diterapkan. Bila apply gagal, status tetap `PENDING` dan endpoint mengembalikan `422 UNPROCESSABLE` — tidak akan ada kondisi "APPROVED tapi belum applied".
- Tabel `attendance_correction_applies` menyimpan: original vs applied clock-in/out, original vs applied status, original vs applied work duration minutes, reviewer, applied_by, applied_at, dan flag `record_created_by_correction`. UNIQUE per `leave_request_id` agar idempotent.
- Pada `attendance_records` ditambah kolom `correction_applied`, `correction_request_id`, `corrected_by`, `corrected_at`, `correction_note` agar UI/report dapat menandai row hasil koreksi tanpa JOIN.
- Bila record absensi sudah ada, hanya `clockInAt`/`clockOutAt`, `status`, `workDurationMinutes`, dan metadata correction yang diubah. Lat/long, device id, location id, face data, dan `attendance_attempts` tidak disentuh.
- Bila record absensi belum ada, dibuat record baru dengan lat/long/device/location/face = `null`. Ini menandai record sebagai correction-generated, bukan absensi mobile asli.

### v1.2.0 baseline (history)

Pada v1.2.0 approved correction hanya muncul sebagai overlay report tanpa mutasi `attendance_records`. v1.3.0 mempertahankan tabel overlay info di report sambil menambah apply engine penuh.

## Reporting & export

- Daily report: tiap row dilengkapi `leaveRequestId`, `leaveType`, `leaveStatus`. Bila user hanya punya approved leave (tanpa attendance), row baru dibuat dengan `status = null`.
- `leaveTotals` di daily report menampilkan jumlah leave per tipe pada tanggal itu.
- CSV / Excel / PDF export menambah kolom `Leave Type` dan `Leave Status`. Baris leave-only memiliki kolom status absensi kosong.
- Tetap tenant-scoped; cross-tenant export ditolak 403.

## Audit log

Action baru pada `audit_logs`:

- `LEAVE_REQUEST_CREATED`
- `LEAVE_REQUEST_APPROVED`
- `LEAVE_REQUEST_REJECTED`
- `LEAVE_REQUEST_CANCELLED`
- `ATTENDANCE_CORRECTION_APPROVED` (keputusan reviewer menyetujui ATTENDANCE_CORRECTION)
- `ATTENDANCE_CORRECTION_APPLIED` (v1.3.0+; perubahan benar-benar diterapkan ke `attendance_records`)
- `ATTENDANCE_CORRECTION_ALREADY_APPLIED` (v1.3.0+; idempotent guard)
- `ATTENDANCE_CORRECTION_APPLY_FAILED` (v1.3.0+; apply gagal, transaksi rollback. Audit dicommit lewat `REQUIRES_NEW` untuk jejak)

Metadata dijaga minimal: `requestType`, `status`, `startDate`, `endDate`, `reasonProvided`, `reviewNoteProvided`. `reason` penuh tidak disimpan ke audit log.

## Notification

Event baru di `NotificationEventType`:

- `LEAVE_REQUEST_CREATED`
- `LEAVE_REQUEST_APPROVED`
- `LEAVE_REQUEST_REJECTED`
- `LEAVE_REQUEST_CANCELLED`

Template default mengirim ke channel `IN_APP`, `EMAIL`, `PUSH` mengikuti registry existing. Publisher dibungkus try/catch — kegagalan notifikasi (RabbitMQ down, dsb.) tidak boleh dan tidak akan menggagalkan create/approve/reject/cancel.

## Web

Menu sidebar **Pengajuan** (`/leave-requests`). Halaman memiliki:

- Filter status, jenis, dan rentang tanggal.
- Tabel daftar pengajuan dengan badge tipe dan status berbahasa Indonesia (Sakit / Izin / Cuti / Dinas luar / Koreksi absensi; Menunggu / Disetujui / Ditolak / Dibatalkan).
- Form buat pengajuan, dengan field tambahan jam clock-in/out hanya saat tipe = Koreksi absensi.
- Tombol Setujui / Tolak / Batalkan dengan field catatan reviewer.
- Empty state: "Belum ada pengajuan izin."
- Bila backend mengembalikan 403, halaman menampilkan pesan "Anda tidak memiliki akses ke halaman pengajuan."
- Untuk request `ATTENDANCE_CORRECTION` ber-status `APPROVED`, kolom Catatan menampilkan teks hijau kecil "Koreksi ini sudah diterapkan ke data absensi." Karena v1.3.0 menjamin status APPROVED berarti apply sukses.

Halaman `/attendance` menambahkan badge `"Dikoreksi"` (variant warning) pada row dengan `correctionApplied=true` agar admin dapat membedakan absensi mobile asli dari hasil koreksi.

## Mobile

Tab **Pengajuan** di bottom navigation:

- List pengajuan milik sendiri (pull to refresh).
- Tombol FAB untuk membuat pengajuan baru (Form dengan date picker + dropdown jenis + alasan; field jam clock-in/out muncul saat tipe = Koreksi absensi).
- Tombol "Batalkan" pada pengajuan ber-status `PENDING`.
- Tidak ada attachment / parent flow / complex date picker hierarchy.

## Limitation v1.3.0

- Belum ada leave balance / accrual / quota.
- Belum ada attachment upload (kolom `attachment_url` reserved di DB tapi tidak diisi).
- Belum ada holiday calendar.
- Belum ada payroll integration penuh — apply correction sudah menerapkan ke `attendance_records` dengan audit trail, tapi belum payroll/timesheet engine penuh.
- Reviewer dibatasi `TENANT_ADMIN` dan `SUPER_ADMIN`; hierarchy manager belum dibuka.
- `PARENT` self-request belum tersedia.
- Mobile belum mendukung approve/reject (admin pakai web).
- Apply tidak punya endpoint manual — apply otomatis terjadi saat reviewer approve. Untuk rollback koreksi, gunakan operasi DB manual dengan jejak di `attendance_correction_applies`.
