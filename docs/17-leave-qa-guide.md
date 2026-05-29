# Leave QA guide

Panduan ini dipakai untuk QA manual leave/permission request v1.2.1. Fokusnya adalah memastikan flow leave/permission foundation v1.2.0 dapat diuji secara lokal tanpa secret production dan tanpa endpoint debug production. Untuk detail policy dan limitation, lihat [`docs/16-leave-permission.md`](16-leave-permission.md).

## Prasyarat local dev

- JDK 21.
- Docker Desktop atau Docker Engine dengan Docker Compose.
- Node.js 20 untuk web dashboard.
- Flutter SDK stable terbaru untuk mobile (opsional, hanya jika QA mobile).
- Postman untuk QA manual request HTTP.

## Jalankan PostgreSQL dan RabbitMQ

Dari root repo:

```powershell
docker compose -f docker/docker-compose.yml up -d
```

Service lokal:

- PostgreSQL: `localhost:5432`, database/user/password default `hadivo`.
- RabbitMQ: `localhost:5672`.
- RabbitMQ management UI: `http://localhost:15672`, user/password `hadivo` / `hadivo`.

## Jalankan backend

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend tersedia di `http://localhost:8080`. Swagger UI tersedia di `http://localhost:8080/swagger-ui.html`.

Notification provider default `mock`, sehingga leave flow dapat berjalan tanpa Resend/FCM. Notification publish failure tidak akan menggagalkan flow leave.

## Jalankan web

```powershell
cd web
npm install
npm run dev
```

Web tersedia di `http://localhost:3000`. Halaman pengajuan ada di `http://localhost:3000/leave-requests`.

## Jalankan mobile (opsional)

```powershell
cd mobile
flutter pub get
flutter run
```

Tab `Pengajuan` ada di bottom navigation.

## Akun demo

`DataSeeder` profile `dev`/`local` menyediakan akun berikut pada tenant demo `11111111-1111-1111-1111-111111111111`:

| Email | Password | Role |
| --- | --- | --- |
| `superadmin@hadivo.local` | `ChangeMe123!` | `SUPER_ADMIN` |
| `employee@hadivo.local` | `ChangeMe123!` | `EMPLOYEE` |
| `student@hadivo.local` | `ChangeMe123!` | `STUDENT` |

Password dapat di-override via env `SEED_SUPER_ADMIN_PASSWORD`. Jangan gunakan akun demo di production.

## Variabel Postman

Collection Postman v1.2.1 memakai variable berikut untuk Leave QA Flow:

- `baseUrl` = `http://localhost:8080`
- `tenantId` = `11111111-1111-1111-1111-111111111111`
- `accessToken` = JWT yang di-set otomatis setelah `Auth / Login`
- `sickLeaveRequestId`, `permissionLeaveRequestId`, `annualLeaveRequestId`, `businessTripLeaveRequestId`, `correctionLeaveRequestId` — diisi otomatis oleh test script masing-masing request create
- `fromDate`, `toDate` — diisi manual untuk daily report dan export

Catatan: request `Approve`/`Reject`/`Cancel` di Postman memakai `permissionLeaveRequestId` sebagai default agar tetap rapi tanpa override per skenario. Untuk menguji approve/reject/cancel terhadap tipe lain, ganti path variabel manual atau gunakan request create yang sesuai terlebih dahulu sehingga variabel target terisi.

## Buat leave request dari mobile

1. Login `employee@hadivo.local` / `ChangeMe123!`.
2. Buka tab `Pengajuan`.
3. Tap FAB `Pengajuan`.
4. Pilih jenis (Sakit / Izin / Cuti / Dinas luar / Koreksi absensi).
5. Pilih tanggal mulai dan tanggal selesai.
6. Untuk `Koreksi absensi`, isi minimal salah satu jam clock-in atau clock-out.
7. Tulis alasan (wajib untuk non-correction).
8. Tap `Kirim pengajuan`.
9. Snackbar `Pengajuan terkirim.` muncul, request masuk daftar dengan status `Menunggu`.

## Buat leave request dari Postman

1. Jalankan `Auth / Login` dengan kredensial `employee@hadivo.local`. Test script otomatis menulis `accessToken`.
2. Jalankan request `Leave QA Flow / Create sick leave request` (atau permission / annual leave / business trip / correction). Test script otomatis menulis id ke variable spesifik (`sickLeaveRequestId`, dst).
3. Jalankan `List leave requests` untuk verifikasi data muncul.

Contoh body sick leave:

```json
{
  "requestType": "SICK",
  "startDate": "2026-06-01",
  "endDate": "2026-06-02",
  "reason": "Demam"
}
```

Contoh body attendance correction:

```json
{
  "requestType": "ATTENDANCE_CORRECTION",
  "startDate": "2026-06-03",
  "endDate": "2026-06-03",
  "requestedClockInTime": "2026-06-03T01:00:00Z",
  "correctionNote": "Lupa clock-in"
}
```

## Admin approve / reject dari web

1. Login `superadmin@hadivo.local` di web.
2. Buka `http://localhost:3000/leave-requests`.
3. Filter status `Menunggu`.
4. Pada row yang ingin diproses, isi optional `Catatan reviewer` dan klik `Setujui` atau `Tolak`.
5. Refresh halaman; status berubah ke `Disetujui` atau `Ditolak`, kolom Aksi menampilkan waktu review.

Dari Postman, jalankan:

- `Approve leave request` → memakai `permissionLeaveRequestId` (atau ganti dengan id lain di path variable).
- `Reject leave request` → idem.

## Requester cancel PENDING

1. Sebagai requester, buka tab `Pengajuan` di mobile atau halaman `/leave-requests` di web.
2. Pilih request dengan status `Menunggu`.
3. Tap `Batalkan` (mobile) atau klik tombol `Batalkan` (web).
4. Status berubah ke `Dibatalkan`.
5. Cancel pada status non-PENDING akan ditolak dengan error `Pengajuan tidak dapat dibatalkan karena bukan PENDING` (HTTP 409).

Dari Postman, jalankan `Cancel leave request` menggunakan `permissionLeaveRequestId`.

## Cek daily report setelah leave approved

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/daily?date={{toDate}}
Authorization: Bearer {{accessToken}}
```

Expected:

- `data.leaveTotals.{TYPE}` berisi jumlah leave approved pada tanggal tersebut, contoh `data.leaveTotals.SICK = 1`.
- `data.rows[*].leaveType`, `data.rows[*].leaveStatus`, dan `data.rows[*].leaveRequestId` terisi pada user yang punya approved leave di tanggal itu.
- Bila user hanya punya approved leave tanpa attendance record, row muncul dengan `status = null` dan field leave terisi.

Web `/attendance` juga menampilkan badge tipe leave di kolom Status.

## Cek CSV / Excel / PDF export

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/export.csv?from={{fromDate}}&to={{toDate}}
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/export.xlsx?from={{fromDate}}&to={{toDate}}
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/reports/attendance/export.pdf?from={{fromDate}}&to={{toDate}}
```

Expected pada ketiga format:

- Header memuat kolom `Leave Type` dan `Leave Status` di akhir.
- Untuk row yang user-nya punya approved leave overlap dengan tanggalnya, dua kolom tersebut berisi enum (`SICK`/`PERMISSION`/`ANNUAL_LEAVE`/`BUSINESS_TRIP`/`ATTENDANCE_CORRECTION` dan `APPROVED`).
- Row leave-only memiliki kolom status absensi kosong dan dua kolom leave terisi.

Range maksimal 31 hari masih berlaku — request di luar range akan dijawab `VALIDATION_FAILED`.

## Cek audit log

Audit log untuk leave dapat dilihat langsung dari database lokal:

```sql
select created_at, action, actor_user_id, resource_id, metadata_json
from audit_logs
where action like 'LEAVE_REQUEST_%' or action = 'ATTENDANCE_CORRECTION_APPROVED'
order by created_at desc
limit 20;
```

Expected action yang muncul: `LEAVE_REQUEST_CREATED`, `LEAVE_REQUEST_APPROVED`, `LEAVE_REQUEST_REJECTED`, `LEAVE_REQUEST_CANCELLED`. Tambahan `ATTENDANCE_CORRECTION_APPROVED` muncul saat tipe `ATTENDANCE_CORRECTION` di-approve. Metadata menyimpan `requestType`, `startDate`, `endDate`, dan flag `reasonProvided`/`reviewNoteProvided` saja — tidak ada konten `reason` mentah.

## Cek notification delivery / log

Default provider mock menulis delivery log per channel. Untuk inspeksi cepat:

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/notification-deliveries?eventType=LEAVE_REQUEST_APPROVED
Authorization: Bearer {{accessToken}}
```

Expected `data.items[*].status` salah satu dari `SENT`, `SKIPPED`, atau `FAILED` tergantung kondisi provider/destination. Bila RabbitMQ down, request approve tetap berhasil — periksa log backend `Failed to publish notification ... to RabbitMQ`. Behavior ini di-cover oleh test `notification publisher failure does not break approval`.

## ATTENDANCE_CORRECTION apply (v1.3.0+)

Untuk panduan QA manual khusus correction apply (termasuk verifikasi DB diff, audit row, dan revert planning) lihat [`docs/18-correction-qa-guide.md`](18-correction-qa-guide.md).


Mulai v1.3.0, approve `ATTENDANCE_CORRECTION` **menerapkan** koreksi ke `attendance_records`. Verifikasi manual:

1. Buat correction request untuk user X tanggal Y dengan `requestedClockInTime` (dan/atau `requestedClockOutTime`).
2. Approve dari web/Postman sebagai `TENANT_ADMIN` / `SUPER_ADMIN`.
3. Status request → `APPROVED`. Jika apply gagal, status tetap `PENDING` dan endpoint mengembalikan `422 UNPROCESSABLE` dengan pesan "Tidak dapat menerapkan koreksi absensi…".
4. Cek perubahan di `attendance_records`:

```sql
select id, clock_in_at, clock_out_at, status, work_duration_minutes,
       correction_applied, correction_request_id, corrected_by, corrected_at
from attendance_records
where tenant_id = '...' and user_id = '...' and date = 'YYYY-MM-DD';
```

`clock_in_at`/`clock_out_at` dan `status` di-update sesuai request, dan kolom correction terisi.

5. Cek audit diff koreksi:

```sql
select original_clock_in_at, applied_clock_in_at,
       original_clock_out_at, applied_clock_out_at,
       original_status, applied_status,
       original_work_duration_minutes, applied_work_duration_minutes,
       record_created_by_correction, applied_by, applied_at
from attendance_correction_applies
where leave_request_id = '...';
```

6. Cek audit log:

```sql
select action, metadata_json from audit_logs
where action in ('ATTENDANCE_CORRECTION_APPROVED','ATTENDANCE_CORRECTION_APPLIED')
order by created_at desc limit 5;
```

7. Buka `/attendance` di web — row hari Y harus menampilkan badge `"Dikoreksi"`. Buka `/leave-requests` — request terkait menampilkan "Koreksi ini sudah diterapkan ke data absensi."

8. Verifikasi data yang TIDAK boleh berubah (existing record): lat/long, device id, location id, face, attempts.

```sql
select clock_in_latitude, clock_in_longitude, clock_in_device_id, clock_in_location_id
from attendance_records where id = '...';
-- nilai harus sama dengan sebelum approve

select count(*) from attendance_attempts where tenant_id = '...';
-- count tidak boleh bertambah karena apply
```

9. Untuk skenario record belum ada saat approve: record baru muncul di `attendance_records` dengan `correction_applied=true`, dan lat/long/device/location/face = `NULL`.

Detail rationale dan limitation ada di [`docs/16-leave-permission.md`](16-leave-permission.md).

## Common troubleshooting

- **Backend gagal start, port 8080 in use** — `netstat -ano | findstr :8080` lalu kill PID lama; pastikan tidak ada `gradlew bootRun` lain.
- **Flyway gagal** — migration cold-fresh; reset volume Postgres dengan `docker compose down -v && docker compose up -d`.
- **Web 401 saat masuk `/leave-requests`** — token kedaluwarsa, login ulang.
- **Mobile gagal connect** — periksa `AppConfig.apiBaseUrl`; di emulator Android pakai `10.0.2.2`.
- **Approve gagal dengan `CONFLICT`** — status leave sudah bukan `PENDING`. Cek status lewat `Get leave request detail`.
- **Create gagal dengan `VALIDATION_FAILED` "Rentang pengajuan maksimal 31 hari"** — sesuaikan rentang agar ≤ 31 hari (cap MVP).
- **Create gagal dengan `CONFLICT` "Sudah ada pengajuan aktif yang beririsan"** — sudah ada `PENDING`/`APPROVED` non-correction overlap. Cancel atau pakai rentang lain.
- **Notification delivery `SKIPPED`** — wajar bila user tidak punya email atau FCM token aktif. Tidak menggagalkan flow.

## Batasan v1.3.0

- Belum ada leave balance / accrual / quota.
- Belum ada attachment upload (kolom `attachment_url` reserved tapi tidak terisi).
- Belum ada holiday calendar.
- Belum ada payroll/timesheet engine penuh — apply correction sudah memutasi `attendance_records` dengan audit trail, tapi belum payroll-grade end-to-end.
- Reviewer dibatasi `TENANT_ADMIN` dan `SUPER_ADMIN`.
- `PARENT` belum dapat self-request.
- Mobile belum mendukung approve/reject (gunakan web).
- Tidak ada endpoint manual untuk apply / rollback correction; rollback memerlukan operasi DB manual dengan jejak `attendance_correction_applies`.
