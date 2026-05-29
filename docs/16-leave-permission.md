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

### ATTENDANCE_CORRECTION limitation v1.2.0

Approve correction **tidak** memutasi `attendance_records` di v1.2.0:

- Jika record absensi sudah ada pada tanggal yang dimaksud, `clockInAt`/`clockOutAt` tetap.
- Jika record absensi belum ada, record baru tidak dibuat.
- Approved correction hanya disimpan sebagai pengajuan ber-status `APPROVED` dan akan ditampilkan pada daily report serta export attendance sebagai informasi correction approved (kolom `Leave Type` = `ATTENDANCE_CORRECTION`).

Keputusan ini diambil karena `attendance_records` belum memiliki field audit trail (`correctionRequestId`, `correctedBy`, `correctedAt`, `correctionNote`). Mutasi langsung berisiko menghilangkan riwayat geofence/device/face attempt asli.

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
- `ATTENDANCE_CORRECTION_APPROVED` (terjadi saat approve untuk `ATTENDANCE_CORRECTION`)

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

## Mobile

Tab **Pengajuan** di bottom navigation:

- List pengajuan milik sendiri (pull to refresh).
- Tombol FAB untuk membuat pengajuan baru (Form dengan date picker + dropdown jenis + alasan; field jam clock-in/out muncul saat tipe = Koreksi absensi).
- Tombol "Batalkan" pada pengajuan ber-status `PENDING`.
- Tidak ada attachment / parent flow / complex date picker hierarchy.

## Limitation v1.2.0

- Belum ada leave balance / accrual / quota.
- Belum ada attachment upload (kolom `attachment_url` reserved di DB tapi tidak diisi).
- Belum ada holiday calendar.
- Belum ada payroll integration.
- Approval `ATTENDANCE_CORRECTION` belum payroll-grade — record absensi tidak diubah, hanya overlay di report/export.
- Reviewer dibatasi `TENANT_ADMIN` dan `SUPER_ADMIN`; hierarchy manager belum dibuka.
- `PARENT` self-request belum tersedia.
- Mobile belum mendukung approve/reject (admin pakai web).
