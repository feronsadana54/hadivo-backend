# Attendance correction QA guide

Panduan ini dipakai untuk QA manual Attendance Correction Apply Engine v1.3.0 / v1.3.1. Fokusnya adalah memastikan koreksi absensi diterapkan dengan jejak audit yang lengkap, tanpa memalsukan data perangkat / geofence / face, dan dapat diuji secara lokal.

Untuk policy umum leave/permission lihat [`docs/16-leave-permission.md`](16-leave-permission.md). Untuk QA leave secara umum lihat [`docs/17-leave-qa-guide.md`](17-leave-qa-guide.md).

## Prasyarat local dev

- JDK 21.
- Docker Desktop atau Docker Engine dengan Docker Compose.
- Node.js 20 untuk web dashboard.
- Postman untuk QA manual request HTTP.
- (opsional) Flutter SDK terbaru untuk QA mobile.

## Jalankan service lokal

```powershell
docker compose -f docker/docker-compose.yml up -d
cd backend
.\gradlew.bat bootRun
```

Backend akan tersedia di `http://localhost:8080`. Notification provider default `mock`, jadi tidak perlu Resend/FCM untuk QA correction.

Di terminal terpisah:

```powershell
cd web
npm install
npm run dev
```

Web tersedia di `http://localhost:3000`.

## Akun demo

`DataSeeder` profile `dev`/`local` membuat akun pada tenant demo `11111111-1111-1111-1111-111111111111`:

| Email | Password | Role |
| --- | --- | --- |
| `superadmin@hadivo.local` | `ChangeMe123!` | `SUPER_ADMIN` |
| `employee@hadivo.local` | `ChangeMe123!` | `EMPLOYEE` |
| `student@hadivo.local` | `ChangeMe123!` | `STUDENT` |

Jangan gunakan akun demo di production.

## Variabel Postman

Collection memakai variable berikut untuk Correction QA Flow:

- `baseUrl` = `http://localhost:8080`
- `tenantId` = `11111111-1111-1111-1111-111111111111`
- `accessToken` = JWT yang di-set otomatis setelah `Auth / Login`
- `correctionLeaveRequestId` = di-set otomatis oleh test script `Create attendance correction request`
- `fromDate`, `toDate` = di-set manual untuk endpoint report/export

## Skenario QA — happy path

1. Login sebagai `employee@hadivo.local` lewat Postman `Auth / Login`. `accessToken` ter-isi otomatis.
2. Jalankan `Correction QA Flow / Create attendance correction request`. Test script menulis id ke `correctionLeaveRequestId`. Status awal `PENDING`.
3. Login sebagai `superadmin@hadivo.local` (atau `TENANT_ADMIN`). `accessToken` di-overwrite.
4. Jalankan `Correction QA Flow / Approve attendance correction request`. Response harus `200 OK` dengan `data.status = APPROVED`.
5. Di web buka `http://localhost:3000/leave-requests`. Row terkait menampilkan badge "Disetujui" + teks hijau "Koreksi ini sudah diterapkan ke data absensi.".
6. Buka `http://localhost:3000/attendance` dengan tanggal yang sama. Row user terkait menampilkan badge `"Dikoreksi"` (variant warning) di kolom Status.

## Skenario QA — verifikasi data DB

Local/dev QA only. **Jangan dilakukan di production.** Sambungkan ke `hadivo-postgres` (mis. lewat `psql` atau client DB).

```sql
-- attendance_records: clock-in/out + status + metadata correction berubah
select id, clock_in_at, clock_out_at, status, work_duration_minutes,
       correction_applied, correction_request_id, corrected_by, corrected_at,
       correction_note
from attendance_records
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and date = '<tanggal>'
  and user_id = '<user>';
```

```sql
-- attendance_correction_applies: original vs applied
select leave_request_id, attendance_record_id, requester_user_id, reviewer_user_id, applied_by,
       original_clock_in_at, applied_clock_in_at,
       original_clock_out_at, applied_clock_out_at,
       original_status, applied_status,
       original_work_duration_minutes, applied_work_duration_minutes,
       record_created_by_correction, applied_at, created_at
from attendance_correction_applies
where leave_request_id = '<leaveRequestId>';
```

```sql
-- audit_logs untuk koreksi
select created_at, action, actor_user_id, resource_id, metadata_json
from audit_logs
where action in (
  'ATTENDANCE_CORRECTION_APPROVED',
  'ATTENDANCE_CORRECTION_APPLIED',
  'ATTENDANCE_CORRECTION_ALREADY_APPLIED',
  'ATTENDANCE_CORRECTION_APPLY_FAILED'
)
order by created_at desc
limit 20;
```

Expected:

- `attendance_records.clock_in_at` / `clock_out_at` sudah sesuai request. `status`, `work_duration_minutes`, `correction_applied=true`, `correction_request_id` = id leave request, `corrected_by` = reviewer.
- `attendance_correction_applies` ada satu row per leave request (UNIQUE per `leave_request_id`). `original_*` merefleksikan state sebelum apply. `applied_status` ≠ `original_status` jika status memang berubah.
- `audit_logs` memuat `LEAVE_REQUEST_APPROVED`, `ATTENDANCE_CORRECTION_APPROVED`, dan `ATTENDANCE_CORRECTION_APPLIED` (terakhir dengan metadata `leaveRequestId`, `attendanceRecordId`, `originalStatus`, `appliedStatus`, dan flag `*Exists` / `*Provided`).

## Skenario QA — verifikasi data tidak dipalsukan

Untuk **existing record** yang dikoreksi:

```sql
select clock_in_latitude, clock_in_longitude, clock_in_device_id, clock_in_location_id,
       clock_out_latitude, clock_out_longitude, clock_out_device_id, clock_out_location_id
from attendance_records
where id = '<recordId>';
```

Nilai harus identik dengan sebelum apply. Apply hanya menyentuh `clock_in_at`/`clock_out_at`/`status`/`work_duration_minutes` + 5 kolom correction metadata.

```sql
select count(*) from attendance_attempts
where tenant_id = '11111111-1111-1111-1111-111111111111';
```

Count tidak boleh berubah karena apply. Apply tidak memanipulasi `attendance_attempts` sama sekali.

Untuk **correction-generated record** (tidak ada record absensi sebelumnya):

```sql
select clock_in_latitude, clock_in_longitude, clock_in_device_id, clock_in_location_id,
       clock_out_latitude, clock_out_longitude, clock_out_device_id, clock_out_location_id
from attendance_records
where id = '<recordId>';
```

Field-field tersebut harus `NULL`. Tidak boleh ada nilai palsu seperti `latitude=0`, `device_id='auto'`, atau koordinat dummy. Apply engine memang menulis NULL agar record jelas sebagai hasil koreksi, bukan absensi mobile asli.

## Skenario QA — daily report dan export

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/daily?date={{fromDate}}
Authorization: Bearer {{accessToken}}
```

Expected JSON:

- `data.rows[*].correctionApplied` = `true` untuk row yang baru saja dikoreksi.
- `data.rows[*].correctionRequestId` = id leave request.
- `data.rows[*].correctedAt` = waktu apply.

Export:

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/export.csv?from={{fromDate}}&to={{toDate}}
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/export.xlsx?from={{fromDate}}&to={{toDate}}
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/export.pdf?from={{fromDate}}&to={{toDate}}
```

Expected:

- Header memuat dua kolom terakhir: `Correction Applied`, `Correction Request ID`.
- Row dengan correction memiliki `Correction Applied = true` dan `Correction Request ID = <uuid>`.
- Row tanpa correction memiliki `Correction Applied = false` dan `Correction Request ID = ` (kosong).
- Range maksimal 31 hari tetap berlaku (return `VALIDATION_FAILED` jika dilanggar).

## Skenario QA — idempotency

Local/dev QA only. **Jangan dilakukan di production.**

1. Sebelum re-approve, paksa status request balik ke `PENDING` lewat SQL untuk menguji idempotency layer:

```sql
update leave_requests
set status = 'PENDING'
where id = '<correctionLeaveRequestId>';
```

2. Jalankan `Correction QA Flow / Approve attendance correction request` lagi.
3. Response tetap `200 OK`. Cek `attendance_correction_applies` — jumlah row untuk `leave_request_id` tersebut harus tetap **satu**.
4. `audit_logs` memuat row baru dengan action `ATTENDANCE_CORRECTION_ALREADY_APPLIED`.

Ini hanya untuk verifikasi internal lokal. Jangan menyentuh status leave request lewat SQL di production.

## Apply failed — konsep dan jejak

Apply correction bisa gagal karena alasan internal (misal koneksi DB putus sebagian, atau invariant aplikasi terlanggar). Skenario ini **tidak perlu** disimulasikan dengan merusak FK atau constraint secara sengaja.

Yang penting diketahui untuk QA:

- Apply correction berjalan di transaksi yang sama dengan approve. Bila apply melempar exception, transaksi parent rollback dan `leave_requests.status` tetap `PENDING`.
- Audit `ATTENDANCE_CORRECTION_APPLY_FAILED` ditulis lewat `AuditLogger` yang memakai `Propagation.REQUIRES_NEW`, sehingga jejak failure tetap committed walau parent tx rollback.
- Endpoint approve mengembalikan `422 UNPROCESSABLE` dengan pesan "Tidak dapat menerapkan koreksi absensi. Coba lagi atau hubungi admin."
- Tidak akan terjadi kondisi "status APPROVED tetapi attendance belum berubah".

Cara verifikasi cepat dari sisi developer: cari `ATTENDANCE_CORRECTION_APPLY_FAILED` di `audit_logs`. Bila tidak pernah ada di lingkungan local QA Anda, itu wajar — apply happy-path memang dirancang berjalan tanpa gangguan.

## Common troubleshooting

- **Backend gagal start, port 8080 in use** — `netstat -ano | findstr :8080` lalu hentikan PID lama.
- **Flyway gagal di migration V9** — biasanya karena schema sudah ada inkonsistensi lokal. Reset lewat `docker compose down -v && docker compose up -d`.
- **Approve correction mengembalikan `VALIDATION_FAILED` "Minimal salah satu jam clock-in atau clock-out…"** — request creator lupa mengisi `requestedClockInTime`/`requestedClockOutTime`. Buat ulang dengan minimal salah satu field.
- **Approve correction mengembalikan `CONFLICT`** — request bukan `PENDING`. Cek status lewat `Get leave request detail`.
- **Approve correction mengembalikan `422 UNPROCESSABLE`** — apply gagal. Cek log backend dan `audit_logs` action `ATTENDANCE_CORRECTION_APPLY_FAILED`. Status request tetap `PENDING`, aman untuk dicoba lagi setelah penyebab diatasi.
- **Web tidak menampilkan badge "Dikoreksi"** — pastikan tanggal yang dilihat sama dengan `startDate` request korel. Refresh halaman atau ganti tanggal di filter.

## Revert planning (dokumentasi saja)

**v1.3.1 belum punya endpoint revert.** Tabel `attendance_correction_applies` adalah satu-satunya sumber resmi diff koreksi dan harus tetap utuh. Bagian ini hanya catatan desain untuk fase mendatang dan tidak akan diimplementasikan tanpa persetujuan eksplisit.

Kenapa revert correction sensitif:

- Memutasi `attendance_records` untuk kedua kalinya berarti menambah satu lapis audit. Hapus / overwrite audit row apply asli akan menghancurkan jejak.
- Perlu kejelasan tentang siapa yang berhak revert (umumnya `TENANT_ADMIN` atau `SUPER_ADMIN` dengan reason wajib).
- Perlu memutuskan apakah revert untuk correction-generated record berarti soft-delete record atau hanya menandai `CANCELLED`. Akan mempengaruhi reporting historis.

Rencana arah revert future (TIDAK diimplementasikan di v1.3.1):

1. Baca `original_clock_in_at` / `original_clock_out_at` / `original_status` / `original_work_duration_minutes` dari `attendance_correction_applies` untuk `leave_request_id` tertentu.
2. Untuk record yang ada sebelumnya: restore empat field tersebut ke `attendance_records`, kosongkan / set ulang metadata correction (mis. `correction_applied=false`, `correction_request_id=null`, `corrected_by=null`, `corrected_at=null`, `correction_note=null`).
3. Untuk correction-generated record (`record_created_by_correction = true`): pilih antara soft-delete (set `cancelled_at` baru) atau mark sebagai dihapus dengan jejak. Keputusan ini akan dibuat saat revert benar-benar dibutuhkan.
4. **Jangan hapus row di `attendance_correction_applies`.** Sebaliknya, simpan reversal sebagai row baru di tabel terpisah (mis. `attendance_correction_reverts`) atau kolom tambahan `reverted_at` / `reverted_by` di apply row — desain akan ditetapkan di fase implementasi.
5. Audit action baru `ATTENDANCE_CORRECTION_REVERTED` dengan metadata `leaveRequestId`, `attendanceRecordId`, `revertedBy`, dan reason.
6. Endpoint dan UI harus dibatasi role admin tenant, dengan double confirmation di web dan field reason wajib.

Untuk QA v1.3.1 cukup pastikan flow apply jelas, audit lengkap, dan tidak ada jalan apapun yang menghapus row di `attendance_correction_applies` (termasuk dari API publik, dari web, atau dari mobile).

## Batasan v1.3.1

- Tidak ada endpoint apply manual; apply hanya terjadi melalui approve di backend.
- Tidak ada endpoint revert correction.
- Belum ada UI revert.
- Belum ada payroll / leave balance / accrual / holiday calendar / attachment upload / real face recognition.
- Reviewer dibatasi `TENANT_ADMIN` dan `SUPER_ADMIN`.
- Mobile belum mendukung approve/reject; admin pakai web.
