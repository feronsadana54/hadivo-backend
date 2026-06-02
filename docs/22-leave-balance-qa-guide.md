# Leave balance QA guide

Panduan ini dipakai untuk QA manual Leave Balance / Quota Foundation v1.4.0 / v1.4.1. Fokusnya memastikan policy, balance, ledger, dan deduction `ANNUAL_LEAVE` berjalan benar dengan jejak audit lengkap dan tidak ada double deduct.

Untuk konsep keseluruhan lihat [`docs/21-leave-balance.md`](21-leave-balance.md). Untuk policy umum leave/permission lihat [`docs/16-leave-permission.md`](16-leave-permission.md). Untuk QA leave secara umum lihat [`docs/17-leave-qa-guide.md`](17-leave-qa-guide.md).

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

Backend tersedia di `http://localhost:8080`. Notification provider default `mock`, jadi tidak perlu Resend/FCM untuk QA balance.

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

Collection memakai variable berikut untuk Leave Balance QA Flow:

- `baseUrl` = `http://localhost:8080`
- `tenantId` = `11111111-1111-1111-1111-111111111111`
- `accessToken` = JWT yang di-set otomatis setelah `Auth / Login`
- `memberUserId` = di-set manual ke userId target (mis. id user `employee@hadivo.local`)
- `balanceYear` = tahun untuk query/adjust (mis. `2026`)
- `adjustmentDays` = jumlah hari penyesuaian admin (mis. `2`)
- `annualLeaveRequestId` = di-set otomatis oleh test script `Create annual leave request`
- `sickLeaveRequestId` = di-set otomatis oleh test script `Create sick leave request`
- `correctionLeaveRequestId` = di-set otomatis oleh test script `Create attendance correction request`
- `fromDate`, `toDate` = di-set manual untuk endpoint create leave

Untuk menemukan `memberUserId`, login dulu sebagai admin lalu jalankan request `Members / List memberships` (jika tersedia) atau query `users` table langsung.

## Skenario QA — policy default

1. Login sebagai `superadmin@hadivo.local` lewat `Auth / Login`. `accessToken` ter-isi otomatis.
2. Jalankan `Leave Balance QA Flow / Get leave policy`. Response harus `200 OK` dengan `data.annualLeaveQuotaDays = 12.00`, semua `*_requires_balance = false`, `active = true`. Tenant baru akan mendapatkan default policy yang dibuat otomatis.
3. Jalankan `Leave Balance QA Flow / Update leave policy` (mis. quota 15). Response `200 OK` dengan `annualLeaveQuotaDays = 15.00`. Audit `LEAVE_POLICY_UPDATED` tercatat.
4. Jalankan `Get leave policy` lagi → kuota baru terlihat dan persist.

## Skenario QA — balance initialization

1. Set `memberUserId` ke id employee target.
2. Jalankan `Get member leave balance`. Response `200 OK`. Balance dibuat otomatis dari policy aktif dengan `usedDays = 0.00`, `adjustedDays = 0.00`, `remainingDays = annualQuotaDays`.
3. Audit `LEAVE_BALANCE_INITIALIZED` tercatat.

Atau via web: login admin → buka `http://localhost:3000/leave-balances` → row untuk member terkait muncul dengan kuota terisi.

## Skenario QA — adjustment

1. Jalankan `Adjust leave balance` (mis. `{ "year": 2026, "days": 2, "note": "Bonus tahunan" }`). Response `200 OK`, `adjustedDays = 2.00`, `remainingDays = quota + 2`.
2. Jalankan `Get member leave balance` lagi → verifikasi nilai baru.
3. Audit `LEAVE_BALANCE_ADJUSTED` tercatat dengan metadata `daysChanged`, `balanceBefore`, `balanceAfter`.

Atau via web: klik tombol **Sesuaikan** pada row, isi modal (tahun, hari, catatan), klik **Simpan**.

## Skenario QA — ANNUAL_LEAVE deduct

1. Login sebagai `employee@hadivo.local`. `accessToken` di-overwrite.
2. Set `fromDate` dan `toDate` (mis. `2026-06-10` s.d. `2026-06-12` = 3 hari).
3. Jalankan `Create annual leave request`. Response `201 Created`, `status = PENDING`, `annualLeaveRequestId` ter-set.
4. Login sebagai admin/super admin.
5. Jalankan `Approve annual leave request`. Response `200 OK`, `status = APPROVED`.
6. Jalankan `Get balance after annual leave deduction`. `usedDays = 3.00`, `remainingDays = quota + adjusted - 3`.
7. Ledger `DEDUCT` tercatat dengan `leave_request_id = annualLeaveRequestId`. Audit `LEAVE_BALANCE_DEDUCTED` tercatat.

## Skenario QA — verifikasi data DB

Local/dev QA only. **Jangan dilakukan di production.** Sambungkan ke `hadivo-postgres` (mis. lewat `psql` atau client DB).

```sql
-- Policy aktif
select id, name, annual_leave_quota_days, sick_leave_requires_balance,
       permission_requires_balance, business_trip_requires_balance, active, updated_at
from leave_policies
where tenant_id = '11111111-1111-1111-1111-111111111111';
```

```sql
-- Balance per user/tahun
select user_id, year, annual_quota_days, used_days, adjusted_days, remaining_days, updated_at
from leave_balances
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and user_id = '<memberUserId>'
  and year = 2026;
```

```sql
-- Ledger untuk user/tahun (urutan terbaru di atas)
select change_type, days_changed, balance_before, balance_after, leave_request_id, note, created_at
from leave_balance_ledgers
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and user_id = '<memberUserId>'
  and year = 2026
order by created_at desc;
```

```sql
-- Audit log untuk balance
select created_at, action, actor_user_id, resource_id, metadata_json
from audit_logs
where action in (
  'LEAVE_POLICY_UPDATED',
  'LEAVE_BALANCE_INITIALIZED',
  'LEAVE_BALANCE_ADJUSTED',
  'LEAVE_BALANCE_DEDUCTED'
)
order by created_at desc
limit 20;
```

Expected:

- `leave_balances.used_days` bertambah sesuai jumlah hari `ANNUAL_LEAVE` yang disetujui.
- `remaining_days = annual_quota_days + adjusted_days - used_days`.
- `leave_balance_ledgers` minimal punya satu row `INITIAL` (saat balance pertama dibuat), satu `ADJUST` per penyesuaian, dan satu `DEDUCT` per `leave_request_id` yang sudah di-approve.
- `audit_logs` memuat keempat action di atas dengan metadata yang sesuai.

## Skenario QA — idempotency (no double deduct)

Local/dev QA only. **Jangan dilakukan di production.**

Karena flow approve normal sudah dijaga oleh status guard (request `APPROVED` tidak bisa di-approve ulang via API), pengujian idempotency dilakukan dengan memaksa balik status request ke `PENDING` lewat SQL:

```sql
update leave_requests
set status = 'PENDING'
where id = '<annualLeaveRequestId>';
```

Lalu jalankan `Approve annual leave request` lagi. Response tetap `200 OK`. Cek:

```sql
select count(*) from leave_balance_ledgers
where leave_request_id = '<annualLeaveRequestId>'
  and change_type = 'DEDUCT';
```

Hasil harus tetap **1**. Service `LeaveBalanceService.deductForApproval` memeriksa ledger DEDUCT yang sudah ada dan skip. Defence-in-depth: partial UNIQUE index `(leave_request_id) WHERE change_type='DEDUCT'` di DB akan menggagalkan INSERT kedua kalau service guard somehow lewat.

`used_days` tidak boleh berubah dibandingkan sebelum re-approve.

Ini hanya untuk verifikasi internal lokal. Jangan menyentuh status leave request lewat SQL di production.

## Skenario QA — insufficient balance

Strategi: pakai range tanggal kecil (2–3 hari) dan turunkan saldo lewat adjust supaya saldo lebih kecil dari request, tanpa mentok di batas range maksimal 31 hari.

1. Login sebagai admin.
2. Jalankan `Adjust leave balance` dengan body kecil-negatif misalnya `{ "year": 2026, "days": -10, "note": "Setup test insufficient" }`. Pastikan sisa saldo cukup kecil (mis. 2 hari).
3. Login sebagai employee, set `fromDate=2026-07-01`, `toDate=2026-07-03` (3 hari).
4. Jalankan `Create annual leave request with insufficient balance`. Response `201 Created`, `status = PENDING`.
5. Login sebagai admin.
6. Jalankan `Approve annual leave with insufficient balance`. Response `400 VALIDATION_FAILED` dengan pesan "Sisa cuti tahunan tidak mencukupi untuk pengajuan ini.".
7. Jalankan `Get balance after annual leave deduction` → `used_days` tidak berubah.
8. Cek `leave_balance_ledgers` → tidak ada `DEDUCT` baru untuk request ini.
9. Cek leave request detail (atau `Leave QA Flow / Get leave request detail`) → `status = PENDING`.

## Skenario QA — SICK / PERMISSION / BUSINESS_TRIP non-deduct

1. Login sebagai employee. Jalankan `Create sick leave request` (3 hari). `sickLeaveRequestId` ter-set.
2. Login sebagai admin. Jalankan `Approve sick leave request`. Response `200 OK`, `status = APPROVED`.
3. Jalankan `Get balance after sick leave approval`. `used_days` **tidak berubah** dibandingkan sebelumnya. Tidak ada ledger `DEDUCT` baru.

Ulangi pola yang sama untuk `PERMISSION` dan `BUSINESS_TRIP`. Default policy v1.4.1 punya `*_requires_balance = false`, jadi tidak ada deduct. Kalau di masa depan flag diubah ke `true`, behavior bisa berbeda — saat ini flag-flag tersebut hanya schema reservation.

## Skenario QA — ATTENDANCE_CORRECTION non-deduct

1. Login sebagai employee. Jalankan `Create attendance correction request` (1 hari, isi `requestedClockInTime`). `correctionLeaveRequestId` ter-set.
2. Login sebagai admin. Jalankan `Approve attendance correction request`. Response `200 OK`, `status = APPROVED`. Apply correction ke `attendance_records` berjalan seperti v1.3.x.
3. Jalankan `Get balance after correction approval`. `used_days` **tidak berubah**. Tidak ada ledger `DEDUCT` baru.

Detail correction apply ada di [`docs/18-correction-qa-guide.md`](18-correction-qa-guide.md).

## Skenario QA — cross-year ditolak

1. Login sebagai employee. Set `fromDate=2026-12-30`, `toDate=2027-01-02`.
2. Jalankan `Create annual leave request`. Response `201 Created`, `status = PENDING`.
3. Login sebagai admin. Jalankan `Approve annual leave request`. Response `400 VALIDATION_FAILED` dengan pesan "Pengajuan cuti lintas tahun belum didukung.".
4. Cek leave request → `status = PENDING`. Balance tidak berubah.

User harus split jadi dua request terpisah.

## Skenario QA — adjustment guard

Semua adjustment harus gagal `400 VALIDATION_FAILED`:

- **Negatif berlebihan** — `{ "year": 2026, "days": -100, "note": "test" }` → "Penyesuaian membuat sisa cuti menjadi negatif".
- **Tanpa note** — `{ "year": 2026, "days": 1 }` → "Catatan penyesuaian wajib diisi".
- **Tanpa days** — `{ "year": 2026, "note": "test" }` → "Jumlah hari penyesuaian wajib diisi".
- **days nol** — `{ "year": 2026, "days": 0, "note": "test" }` → "Jumlah hari tidak boleh nol".
- **Year terlalu jauh** — `{ "year": 2020, "days": 1, "note": "test" }` → "Tahun penyesuaian harus antara …".

Quota policy juga divalidasi: PUT `/leave-policy` dengan `annualLeaveQuotaDays > 365` atau `< 0` → `400 VALIDATION_FAILED`.

## Skenario QA — akses

- **Employee adjust** — login sebagai employee, jalankan `Adjust leave balance` → `403 FORBIDDEN`.
- **Employee read other user** — login sebagai employee A, akses `Get member leave balance` dengan `memberUserId = employee B` → `403 FORBIDDEN`.
- **Cross-tenant** — login sebagai admin tenant A, akses path `/api/v1/tenants/{tenantId-B}/leave-policy` atau `.../leave-balances` → `403 FORBIDDEN`.

## Skenario QA — web `/leave-balances`

1. Login admin di `http://localhost:3000/login`.
2. Buka `http://localhost:3000/leave-balances`.
3. Verifikasi:
   - Year filter (default tahun berjalan, opsi `currentYear-1..+1`).
   - Search anggota berdasarkan nama / email.
   - Tabel kolom: Anggota, Tahun, Kuota, Terpakai, Penyesuaian, Sisa (badge), Aksi.
   - Badge sisa: hijau (≥3), kuning (<3), merah (≤0).
   - Tombol **Sesuaikan** membuka modal.
   - Modal: pilih tahun, isi hari (positif/negatif/desimal step 0.5), isi catatan (wajib). Klik **Simpan**.
   - Setelah sukses, banner hijau "Saldo cuti … berhasil diperbarui." muncul 4 detik.
   - Empty state: "Belum ada data saldo cuti." kalau tidak ada balance.
   - Error state pakai banner merah dengan pesan dari backend.
   - Responsive: di layar sempit, tabel scroll horizontal; sidebar berubah jadi top-nav.

## Skenario QA — mobile profile balance card

1. Jalankan mobile app, login sebagai `employee@hadivo.local`.
2. Buka tab **Profile**.
3. Verifikasi card "Sisa Cuti Tahunan":
   - Saat fetch berhasil: "Sisa N hari dari Q hari", baris kedua "Tahun YYYY • Terpakai X hari".
   - Saat fetch error / saldo belum dibuat: "Belum tersedia". Tidak boleh crash.
4. Pastikan tidak ada tombol adjust di mobile. Adjustment hanya dari admin web.

## Common troubleshooting

- **Backend gagal start, port 8080 in use** — `netstat -ano | findstr :8080` lalu hentikan PID lama.
- **Flyway gagal di migration V10** — biasanya schema sudah ada inkonsistensi lokal. Reset lewat `docker compose down -v && docker compose up -d`.
- **Adjust gagal `VALIDATION_FAILED`** — cek pesan backend. Kemungkinan note kosong, days = 0, year di luar rentang, atau hasil negatif. Web modal menampilkan pesan generik karena `errorMessageByCode` memetakan `VALIDATION_FAILED` ke pesan umum; backend log dan response `error.message` (di Postman) memberi detail lebih spesifik.
- **Approve `ANNUAL_LEAVE` mengembalikan `400 VALIDATION_FAILED` walau saldo cukup** — cek `startDate.year` dan `endDate.year`. Kalau berbeda, ini cross-year limit MVP. Split request jadi dua.
- **Saldo tidak berubah setelah approve** — pastikan `requestType = ANNUAL_LEAVE`. SICK / PERMISSION / BUSINESS_TRIP / ATTENDANCE_CORRECTION tidak mengurangi saldo di v1.4.x.
- **Web `/leave-balances` empty padahal sudah ada user** — balance dibuat lazy; akses `Get member leave balance` minimal sekali untuk men-trigger init, atau tunggu hingga request `ANNUAL_LEAVE` pertama disetujui.
- **Mobile card menunjukkan "Belum tersedia" terus** — pastikan backend running, JWT valid (`sub` claim berisi userId), dan tenant id di `AppConfig` cocok dengan tenant user.

## Batasan v1.4.1

- Tidak ada accrual bulanan / kuartalan / pro-rata.
- Tidak ada carry-forward antar tahun.
- Tidak ada half-day leave atau hourly leave.
- Tidak ada holiday calendar / workday-only calculation.
- Tidak ada cancel approved leave / restore balance. `RESTORE` change type sudah ada di schema tapi belum punya flow yang menulisnya.
- Tidak ada payroll integration.
- Tidak ada attachment upload untuk pengajuan cuti.
- Reviewer dibatasi `TENANT_ADMIN` dan `SUPER_ADMIN`.
- Mobile belum mendukung adjust atau edit policy; admin pakai web.
- Saldo tidak boleh negatif lewat adjust admin.
- Cross-year `ANNUAL_LEAVE` belum didukung; request harus dibagi per tahun.
