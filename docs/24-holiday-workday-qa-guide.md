# Holiday / Workday QA guide

Panduan ini dipakai untuk QA manual Holiday / Workday Calendar v1.5.0 yang distabilkan di v1.5.1. Fokusnya memastikan tenant workday settings, daftar hari libur, dan deduksi `ANNUAL_LEAVE` berbasis hari kerja berjalan benar dengan jejak audit lengkap.

Untuk konsep schema, endpoint, dan contoh perhitungan lihat [`docs/23-holiday-workday-calendar.md`](23-holiday-workday-calendar.md). Untuk leave balance / quota lihat [`docs/21-leave-balance.md`](21-leave-balance.md) dan QA-nya di [`docs/22-leave-balance-qa-guide.md`](22-leave-balance-qa-guide.md).

## Prasyarat local dev

- JDK 21.
- Docker Desktop atau Docker Engine + Docker Compose.
- Node.js 20 untuk web dashboard.
- Postman untuk QA HTTP request.
- (opsional) Flutter SDK terbaru jika ingin verifikasi mobile.

## Jalankan service lokal

```powershell
docker compose -f docker/docker-compose.yml up -d
cd backend
.\gradlew.bat bootRun
```

Backend berjalan di `http://localhost:8080`. Notification provider default `mock`, jadi tidak perlu Resend/FCM untuk QA workday/holiday.

Di terminal terpisah:

```powershell
cd web
npm install
npm run dev
```

Web tersedia di `http://localhost:3000`.

(Opsional) Mobile:

```powershell
cd mobile
flutter pub get
flutter run --dart-define=HADIVO_API_BASE_URL=http://localhost:8080
```

## Akun demo

`DataSeeder` profile `dev`/`local` membuat akun pada tenant demo `11111111-1111-1111-1111-111111111111`:

| Email | Password | Role |
| --- | --- | --- |
| `superadmin@hadivo.local` | `ChangeMe123!` | `SUPER_ADMIN` |
| `employee@hadivo.local` | `ChangeMe123!` | `EMPLOYEE` |
| `student@hadivo.local` | `ChangeMe123!` | `STUDENT` |

Jangan gunakan akun demo di production.

## Variabel Postman

Collection memakai variable berikut untuk Holiday Workday QA Flow:

- `baseUrl` = `http://localhost:8080`
- `tenantId` = `11111111-1111-1111-1111-111111111111`
- `accessToken` = JWT yang di-set otomatis setelah `Auth / Login`
- `memberUserId` = di-set manual ke userId target (mis. id user `employee@hadivo.local`)
- `balanceYear` = tahun untuk query saldo (mis. `2026`)
- `holidayId` = di-set otomatis oleh test script `Create custom holiday` / `Create active holiday in annual leave range`
- `holidayDate` = tanggal default untuk holiday utama (mis. `2026-09-15`)
- `holidayRangeFrom` = batas bawah list holiday (mis. `2026-01-01`)
- `holidayRangeTo` = batas atas list holiday (mis. `2026-12-31`)
- `annualLeaveRequestId` = di-set otomatis oleh test script tiap request `Create annual leave ...`

Tanggal pada Postman dipilih agar tidak bentrok dengan holiday yang sudah dibuat di test atau skenario sebelumnya: `2026-09-04..09-07` (Jum–Sen), `2026-09-12..09-13` (Sab–Min), `2026-09-14..09-16` (Sen–Rab) dengan `2026-09-15` (Selasa) sebagai active holiday.

## Skenario QA — default workday settings

1. Login sebagai admin lewat `Auth / Login`.
2. Jalankan `Holiday Workday QA Flow / Get workday settings`. Response `200 OK`. Field `mondayWorkday..fridayWorkday = true`, `saturdayWorkday = false`, `sundayWorkday = false`, `active = true`. Tenant baru otomatis dibuatkan row default Mon–Fri pada GET pertama.
3. Verifikasi di web: buka `http://localhost:3000/calendar` → card **Hari Kerja** → centang Senin–Jumat aktif, Sabtu–Minggu tidak.

## Skenario QA — update workday settings

1. Jalankan `Update workday settings` dengan body default Postman `{ "saturdayWorkday": true }`. Response `200 OK`, `saturdayWorkday = true`.
2. Audit `WORKDAY_SETTINGS_UPDATED` tercatat dengan metadata `changedFields: ["saturdayWorkday"]`.
3. Set kembali ke default lewat body `{ "saturdayWorkday": false }` sebelum lanjut ke skenario deduct, agar contoh Jum–Sen tetap menghasilkan 2 workdays.
4. **Edge case**: kirim semua hari `false` (`mondayWorkday..sundayWorkday = false`). Expected `400 VALIDATION_FAILED` pesan "Minimal satu hari dalam seminggu harus menjadi hari kerja". Tidak ada audit log.
5. **Role**: login sebagai employee → jalankan `Update workday settings` → expected `403 FORBIDDEN`. Workday settings tidak berubah.

## Skenario QA — create / list / disable holiday

1. Login sebagai admin. Jalankan `Create custom holiday` (`holidayDate=2026-09-15`, `name=Cuti Bersama QA`, `type=COMPANY`). Response `201 Created`, `active=true`. `holidayId` ter-set otomatis. Audit `HOLIDAY_CREATED` tercatat.
2. Jalankan `List holidays` dengan `from=2026-01-01`, `to=2026-12-31`. Response `200 OK`. Holiday baru muncul. Sorted ASC by `holidayDate`.
3. Jalankan `Disable holiday` (`PATCH active=false`). Response `200 OK`, `active=false`. Audit `HOLIDAY_UPDATED` dengan `changedFields: ["active"]`.
4. **Tidak ada DELETE endpoint** — pakai PATCH `active=false`. Holiday inactive tetap muncul di list tetapi tidak ikut workday calculation.
5. **Edge cases**:
   - **Duplicate** — jalankan `Create custom holiday` lagi dengan payload identik → `409 CONFLICT` pesan "Hari libur dengan tanggal dan nama yang sama sudah ada".
   - **Missing holidayDate** — body `{ "name": "Tanpa Tanggal", "type": "CUSTOM" }` → `400 VALIDATION_FAILED` pesan "Tanggal hari libur wajib diisi".
   - **Blank name** — body `{ "holidayDate": "2026-09-20", "name": "   ", "type": "CUSTOM" }` → `400 VALIDATION_FAILED` pesan "Nama hari libur wajib diisi".
   - **Invalid type** — body `{ "holidayDate": "2026-09-21", "name": "Tipe Aneh", "type": "RANDOM" }` → `400 VALIDATION_FAILED` pesan "Tipe hari libur tidak valid…".
   - **List from > to** — `GET /holidays?from=2026-07-01&to=2026-06-01` → `400 VALIDATION_FAILED` "Tanggal mulai tidak boleh setelah tanggal akhir".
   - **List range > 366 days** — `GET /holidays?from=2026-01-01&to=2027-06-01` → `400 VALIDATION_FAILED` "Rentang pencarian hari libur maksimal 366 hari".
6. **Role**: login sebagai employee → jalankan `Create custom holiday` → `403 FORBIDDEN`. Tetapi `List holidays` dan `Get workday settings` tetap `200 OK`.

## Skenario QA — Jumat–Senin deduct = 2 hari kerja

Default workday Mon–Fri. Range `2026-09-04` (Jumat) s.d. `2026-09-07` (Senin) = 4 hari kalender, 2 workdays.

1. Pastikan `saturdayWorkday=false` (default). Kalau sebelumnya di-set true, kirim `Update workday settings` body `{ "saturdayWorkday": false }`.
2. Login sebagai requester (`employee@hadivo.local`).
3. Jalankan `Create annual leave Friday to Monday` (`startDate=2026-09-04`, `endDate=2026-09-07`). Response `201 Created`, `status=PENDING`, `annualLeaveRequestId` ter-set.
4. Login sebagai admin.
5. Jalankan `Approve annual leave Friday to Monday`. Response `200 OK`, `status=APPROVED`.
6. Jalankan `Get balance after Friday-Monday deduction`. `usedDays` bertambah **2.00**, bukan 4.00.
7. Audit `LEAVE_BALANCE_DEDUCTED` tercatat dengan `daysChanged=2.00`. Ledger `DEDUCT` ditulis dengan `leave_request_id = annualLeaveRequestId`.

## Skenario QA — Sabtu–Minggu only ditolak

Default workday Mon–Fri. Range `2026-09-12` (Sabtu) s.d. `2026-09-13` (Minggu) = 0 workdays.

1. Login sebagai requester. Jalankan `Create annual leave only weekend`. Response `201 Created`, `status=PENDING`. `annualLeaveRequestId` di-overwrite.
2. Login sebagai admin. Jalankan `Approve annual leave only weekend (expected fail)`. Response `400 VALIDATION_FAILED` pesan "Pengajuan cuti tidak memiliki hari kerja yang dapat dipotong.".
3. Jalankan `Get balance after Friday-Monday deduction` (atau request `Get member leave balance` di Leave Balance QA Flow). `usedDays` **tidak berubah**.
4. Cek `leave_balance_ledgers`: tidak ada `DEDUCT` baru untuk `annualLeaveRequestId` ini.
5. Cek leave request detail: `status` tetap `PENDING`.

## Skenario QA — holiday aktif mengurangi workday count

Default workday Mon–Fri. Range `2026-09-14` (Senin) s.d. `2026-09-16` (Rabu). Tanpa holiday = 3 workdays. Dengan holiday aktif di Selasa `2026-09-15` = 2 workdays.

1. Login sebagai admin. Jalankan `Create active holiday in annual leave range` (`holidayDate=2026-09-15`, `name=Holiday Aktif QA Range`, `active=true`). `holidayId` ter-set.
2. Login sebagai requester. Jalankan `Create annual leave with active holiday` (`startDate=2026-09-14`, `endDate=2026-09-16`). Response `201`, `status=PENDING`.
3. Login sebagai admin. Jalankan `Approve annual leave with active holiday`. Response `200 OK`, `status=APPROVED`.
4. Jalankan `Get balance after holiday deduction`. `usedDays` bertambah **2.00** (bukan 3.00).
5. Audit `LEAVE_BALANCE_DEDUCTED` dengan `daysChanged=2.00`. Ledger `DEDUCT` ditulis.

## Skenario QA — holiday inactive tidak mengurangi workday count

1. Lanjutkan dari skenario sebelumnya. Jalankan `Disable holiday` untuk `holidayId` Selasa 2026-09-15 (`PATCH active=false`).
2. Login sebagai requester. Buat pengajuan ANNUAL_LEAVE baru yang melewati tanggal yang sama, mis. `startDate=2026-09-21` (Sen) s.d. `2026-09-23` (Rab) — tanpa active holiday di range tersebut. Response `201`, `status=PENDING`.
3. Login sebagai admin. Approve request. Response `200 OK`. Verifikasi `usedDays` bertambah **3.00** karena tidak ada active holiday yang di-skip. Holiday inactive **tidak mempengaruhi** count.
4. (Opsional) Verifikasi langsung lewat service: jalankan `WorkdayCalendarService.countWorkdays(tenantId, 2026-09-14, 2026-09-16)` setelah holiday di-disable → mengembalikan 3.

## Skenario QA — verifikasi data DB

Local/dev QA only. **Jangan dilakukan di production.** Sambungkan ke `hadivo-postgres` (mis. lewat `psql` atau client DB).

```sql
-- Workday settings tenant
select tenant_id, monday_workday, tuesday_workday, wednesday_workday,
       thursday_workday, friday_workday, saturday_workday, sunday_workday, active, updated_at
from tenant_workday_settings
where tenant_id = '11111111-1111-1111-1111-111111111111';
```

```sql
-- Holiday tenant pada tahun tertentu
select holiday_date, name, type, active, created_at, updated_at
from tenant_holidays
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and holiday_date between '2026-01-01' and '2026-12-31'
order by holiday_date asc;
```

```sql
-- Ledger DEDUCT untuk user/tahun
select change_type, days_changed, balance_before, balance_after,
       leave_request_id, note, created_at
from leave_balance_ledgers
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and user_id = '<memberUserId>'
  and year = 2026
order by created_at desc;
```

```sql
-- Audit workday/holiday/deduct
select created_at, action, actor_user_id, resource_id, metadata_json
from audit_logs
where action in (
  'WORKDAY_SETTINGS_UPDATED',
  'HOLIDAY_CREATED',
  'HOLIDAY_UPDATED',
  'LEAVE_BALANCE_DEDUCTED'
)
order by created_at desc
limit 20;
```

Expected:

- `tenant_workday_settings` punya 1 row per tenant, `UNIQUE tenant_id`. Default Mon–Fri true, Sat–Sun false.
- `tenant_holidays` boleh banyak row per tenant. `(tenant_id, holiday_date, name)` UNIQUE menjaga tidak ada duplikat identik.
- `leave_balance_ledgers.days_changed` untuk row `DEDUCT` adalah **workday count** (mis. 2.00 untuk Jum–Sen), bukan kalender count.
- `audit_logs` memuat keempat action di atas dengan metadata yang sesuai (`changedFields`, `holidayDate`, `type`, `daysChanged`, dst).

## Skenario QA — cross-year limitation

`ANNUAL_LEAVE` lintas tahun belum didukung di v1.5.1 (lanjutan dari v1.4.x). Verifikasi:

1. Login sebagai requester. Buat ANNUAL_LEAVE `startDate=2026-12-30`, `endDate=2027-01-02`. Response `201 PENDING`.
2. Login sebagai admin. Approve. Expected `400 VALIDATION_FAILED` pesan "Pengajuan cuti lintas tahun belum didukung.". Request tetap PENDING, saldo tidak berubah, tidak ada DEDUCT ledger.

User harus split jadi dua request terpisah.

## Skenario QA — akses

- **Employee update workday settings** — `403 FORBIDDEN`.
- **Employee create / patch holiday** — `403 FORBIDDEN`.
- **Employee read** (`GET /workday-settings` dan `GET /holidays`) — `200 OK`.
- **Cross-tenant** — admin tenant A mengakses path `/api/v1/tenants/{tenantId-B}/workday-settings` atau `.../holidays` → `403 FORBIDDEN`.

## Skenario QA — web `/calendar`

1. Login admin di `http://localhost:3000/login`.
2. Buka `http://localhost:3000/calendar`.
3. Verifikasi UI:
   - Header "Kalender Kerja" + icon `CalendarDays`.
   - Card **Hari Kerja**: 7 checkbox Senin–Minggu, tombol Simpan. Centang default Mon–Fri. Klik Sabtu lalu Simpan → banner sukses "Hari kerja diperbarui." muncul beberapa detik. Reload → state persist.
   - Centang semua hari off lalu Simpan → error inline "Minimal satu hari dalam seminggu harus menjadi hari kerja" (pesan dari backend).
   - Card **Tambah Hari Libur**: input tanggal, nama, dropdown tipe (Kustom/Nasional/Perusahaan/Sekolah), tombol Tambah. Submit valid → banner "Hari libur ditambahkan.". Submit tanpa tanggal → error inline "Tanggal hari libur wajib diisi.".
   - Card **Daftar Hari Libur**: dropdown tahun (currentYear-1..+1), tabel Tanggal/Nama/Tipe/Status, badge tipe biru dan status hijau/abu. Tombol "Aktifkan" / "Nonaktifkan" toggle holiday.
   - Empty state: pilih tahun yang tidak punya holiday → "Belum ada hari libur. Tambahkan hari libur lewat form di atas.".
   - Error state: matikan backend sebentar → error banner merah dengan pesan dari client.
   - Responsive: layar sempit → card stack vertikal, tabel scroll horizontal, sidebar berubah jadi top-nav.

## Skenario QA — mobile

Tidak ada perubahan mobile di v1.5.x. Mobile tetap dapat:

- Membuat leave request (`requestType=ANNUAL_LEAVE`).
- Membaca saldo cuti di Profile card.

Backend response tetap additive (`leaveDays` opsional). Mobile parser mengabaikan field yang tidak dikenal. Tidak ada UI mobile untuk workday/holiday settings.

## Common troubleshooting

- **Backend gagal start, port 8080 in use** — `netstat -ano | findstr :8080`, hentikan PID lama, lalu start ulang.
- **Flyway gagal di migration V11** — schema lokal inkonsisten. Reset lewat `docker compose down -v && docker compose up -d`.
- **Approve ANNUAL_LEAVE Sat–Sun tidak return 400** — cek workday settings tenant. Kalau Sabtu/Minggu di-set true sebelumnya, count > 0 dan deduct tetap jalan. Set kembali ke default lewat `Update workday settings`.
- **Friday–Monday deduct 4 bukan 2** — workday settings memasukkan Sab/Min sebagai workday. Reset ke default Mon–Fri, hapus saldo via SQL kalau perlu, lalu re-run skenario.
- **Active holiday tidak mengurangi count** — pastikan `holidayDate` jatuh tepat di salah satu tanggal range dan `active=true`. Cek `tenant_holidays` lewat SQL.
- **Duplicate holiday saat seed ulang** — pakai nama berbeda, atau disable holiday lama (`PATCH active=false`) sebelum membuat ulang. Tidak ada DELETE endpoint.
- **Web `/calendar` form tidak menerima tanggal** — pastikan browser locale mendukung input `type=date`. Pakai format `YYYY-MM-DD` langsung di field jika browser tidak menyediakan date picker.
- **Saldo tidak berubah setelah Approve berhasil** — pastikan `requestType=ANNUAL_LEAVE`. SICK/PERMISSION/BUSINESS_TRIP/ATTENDANCE_CORRECTION tidak mengurangi saldo. Lihat [`docs/22-leave-balance-qa-guide.md`](22-leave-balance-qa-guide.md).
- **Audit log kosong** — pastikan request memang mengubah field. PATCH holiday yang body-nya identik dengan state existing tidak menulis `changedFields` baru.

## Batasan v1.5.1

- Belum ada auto sync libur nasional dari provider resmi.
- Belum ada regional holiday kompleks (per-cabang / per-kota).
- Belum ada half-day leave atau hourly leave.
- Belum ada calendar import dari file (.ics, CSV, Excel).
- Belum ada Google Calendar / Microsoft 365 integration.
- Belum ada payroll integration.
- Belum ada workday override per user — settings tenant-level berlaku untuk semua anggota.
- Belum ada bulk create holiday.
- Belum ada DELETE endpoint untuk holiday — soft-disable dengan PATCH `active=false`.
- Workday calculation hanya dipakai untuk ANNUAL_LEAVE deduction; shift / late threshold / payroll masih kalender.
- Cross-year ANNUAL_LEAVE tetap ditolak; user harus split jadi dua request terpisah.
- Face recognition asli, payment flow, dan attendance correction apply engine tidak berubah di v1.5.1.
