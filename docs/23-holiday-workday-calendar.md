# Holiday / Workday Calendar Foundation

Dokumen ini menjelaskan fondasi kalender hari kerja dan hari libur Hadivo v1.5.0. Tujuannya: tenant bisa mendefinisikan hari kerja mingguan dan hari libur, dan `ANNUAL_LEAVE` di-deduct berdasarkan **hari kerja** bukan hari kalender.

Untuk panduan QA manual workday/holiday lihat [`docs/24-holiday-workday-qa-guide.md`](24-holiday-workday-qa-guide.md). Untuk konsep leave balance lihat [`docs/21-leave-balance.md`](21-leave-balance.md). Untuk QA leave balance lihat [`docs/22-leave-balance-qa-guide.md`](22-leave-balance-qa-guide.md).

## Konsep

Dua tabel baru di V11:

- **`tenant_workday_settings`** — satu row per tenant (`UNIQUE tenant_id`). Berisi 7 boolean `monday_workday`..`sunday_workday`. Default: Senin–Jumat `true`, Sabtu–Minggu `false`. Service-level guard menolak update yang membuat semua hari `false`.
- **`tenant_holidays`** — banyak row per tenant. Berisi `holiday_date`, `name`, `type` (CUSTOM / NATIONAL / COMPANY / SCHOOL), `active`. UNIQUE `(tenant_id, holiday_date, name)` mencegah duplikat identik. Tidak ada endpoint DELETE — pakai PATCH `active=false` untuk soft-disable.

Workday calculation:

```
workdays = jumlah tanggal dalam [start, end] yang:
  - workday_settings.isWorkday(dayOfWeek) == true
  - tidak ada holiday aktif di tanggal tersebut
```

Range maksimal 31 hari (mengikuti `MAX_RANGE_DAYS` leave request).

## Endpoint

### Workday settings

| Method | Path | Akses |
| --- | --- | --- |
| `GET` | `/api/v1/tenants/{tenantId}/workday-settings` | semua member |
| `PUT` | `/api/v1/tenants/{tenantId}/workday-settings` | `TENANT_ADMIN`, `SUPER_ADMIN` |

GET pertama untuk tenant baru otomatis membuat default Mon–Fri. PUT body (semua field opsional):

```json
{
  "mondayWorkday": true,
  "tuesdayWorkday": true,
  "wednesdayWorkday": true,
  "thursdayWorkday": true,
  "fridayWorkday": true,
  "saturdayWorkday": false,
  "sundayWorkday": false,
  "active": true
}
```

Validasi: minimal satu hari `true` setelah merge → kalau tidak, `400 VALIDATION_FAILED` "Minimal satu hari dalam seminggu harus menjadi hari kerja". Audit `WORKDAY_SETTINGS_UPDATED` mencatat `changedFields`.

### Holiday

| Method | Path | Akses |
| --- | --- | --- |
| `GET` | `/api/v1/tenants/{tenantId}/holidays?from=YYYY-MM-DD&to=YYYY-MM-DD` | semua member |
| `POST` | `/api/v1/tenants/{tenantId}/holidays` | `TENANT_ADMIN`, `SUPER_ADMIN` |
| `PATCH` | `/api/v1/tenants/{tenantId}/holidays/{holidayId}` | `TENANT_ADMIN`, `SUPER_ADMIN` |

GET parameter `from` / `to` opsional. Default: 90 hari ke belakang sampai 365 hari ke depan dari `LocalDate.now()`. Range max 366 hari; `from > to` ditolak.

POST body:

```json
{
  "holidayDate": "2026-06-10",
  "name": "Cuti Bersama",
  "type": "COMPANY",
  "active": true
}
```

Validasi: `holidayDate` & `name` wajib. `type` harus salah satu dari CUSTOM / NATIONAL / COMPANY / SCHOOL (default CUSTOM). Duplicate `(date, name)` → `409 CONFLICT` "Hari libur dengan tanggal dan nama yang sama sudah ada".

PATCH semua field opsional. Audit `HOLIDAY_CREATED` dan `HOLIDAY_UPDATED` mencatat metadata yang sesuai.

**Tidak ada endpoint DELETE** — soft-disable dengan `PATCH active=false`. Holiday inactive tidak ikut perhitungan workday.

## Annual leave deduction impact

`LeaveBalanceService.deductForApproval` v1.5.0 mengganti perhitungan kalender lama:

```kotlin
// SEBELUM (v1.4.x): kalender count
val days = ChronoUnit.DAYS.between(start, end) + 1

// SEKARANG (v1.5.0): workday count
val workdays = workdayCalendar.countWorkdays(tenantId, start, end)
if (workdays <= 0) throw VALIDATION_FAILED
val days = BigDecimal(workdays)
```

Konsekuensi:

- ANNUAL_LEAVE Jum–Sen (Sat/Sun default off) → 4 kalender, **2 workdays**, deduct 2.
- ANNUAL_LEAVE Sab–Min (semua weekend) → 0 workdays → **ditolak** dengan `400 VALIDATION_FAILED` "Pengajuan cuti tidak memiliki hari kerja yang dapat dipotong." Request tetap `PENDING`.
- Holiday aktif yang jatuh pada workday akan mengurangi count (mis. Senin yang ditandai holiday → 0 day, bukan 1).
- Holiday yang jatuh di hari yang sudah non-workday (mis. Sabtu) **tidak menambah/mengurangi apa-apa** — Sabtu sudah tidak dihitung sejak awal.
- Holiday `active=false` tidak diperhitungkan.

Cross-year guard dan idempotency guard dari v1.4.x tetap berlaku. SICK / PERMISSION / BUSINESS_TRIP / ATTENDANCE_CORRECTION tetap tidak deduct.

## Contoh

### Contoh 1 — Jum–Sen dengan weekend default off

Tenant pakai default Mon–Fri workday. Karyawan ajukan ANNUAL_LEAVE `2026-06-05` (Jumat) s.d. `2026-06-08` (Senin).

- Kalender: 4 hari (Jum, Sab, Min, Sen)
- Workday calc: Jum (work) + Sab (off) + Min (off) + Sen (work) = **2 workdays**
- Saldo turun 2.

### Contoh 2 — Sabtu aktif sebagai hari kerja

Tenant PUT workday `saturdayWorkday=true`. Karyawan ajukan ANNUAL_LEAVE `2026-06-05` s.d. `2026-06-08`.

- Kalender: 4 hari
- Workday calc: Jum (work) + Sab (work, sekarang on) + Min (off) + Sen (work) = **3 workdays**
- Saldo turun 3.

### Contoh 3 — Hari libur aktif

Tenant pakai default Mon–Fri. Admin tambah holiday `2026-06-09` (Selasa) dengan `active=true`. Karyawan ajukan ANNUAL_LEAVE `2026-06-08` s.d. `2026-06-10`.

- Kalender: 3 hari (Sen, Sel, Rab)
- Workday calc: Sen (work) + Sel (holiday, skip) + Rab (work) = **2 workdays**
- Saldo turun 2.

### Contoh 4 — Cuti hanya weekend

Default workday Mon–Fri. ANNUAL_LEAVE `2026-06-06` (Sabtu) s.d. `2026-06-07` (Minggu).

- Kalender: 2 hari
- Workday calc: Sab (off) + Min (off) = **0 workdays**
- Approve → `400 VALIDATION_FAILED`. Request tetap `PENDING`. Saldo tidak berubah, tidak ada ledger `DEDUCT`.

## Audit actions

- `WORKDAY_SETTINGS_UPDATED` — metadata `changedFields`.
- `HOLIDAY_CREATED` — metadata `holidayDate`, `type`, `active`.
- `HOLIDAY_UPDATED` — metadata `holidayId`, `changedFields`.

`LEAVE_BALANCE_DEDUCTED` v1.4.0 tetap dipakai, dengan `daysChanged` sekarang berisi workday count.

## Web Dashboard

Page baru **`/calendar`** (judul: Kalender Kerja) dengan 3 card:

- **Hari Kerja** — checkbox Senin–Minggu, tombol Simpan, error inline (mis. "Minimal satu hari…").
- **Tambah Hari Libur** — form tanggal + nama + tipe (CUSTOM/NATIONAL/COMPANY/SCHOOL).
- **Daftar Hari Libur** — filter tahun (`currentYear-1..+1`), tabel tanggal/nama/tipe/status, aksi "Aktifkan"/"Nonaktifkan".

Sidebar entry "Kalender Kerja" (icon `CalendarDays`) di antara "Saldo Cuti" dan "Notifikasi".

Page `/leave-requests` menambahkan label "X hari kerja" pada kolom Periode untuk row `ANNUAL_LEAVE` saat backend mengirim `leaveDays`. Kalau field tidak ada (mis. cross-year), fallback ke kalender count.

## Mobile App

**Tidak ada perubahan mobile.** Parser leave request mengabaikan field tambahan `leaveDays` secara natural. Saldo cuti di profile card tetap akurat karena backend hitung deduct dengan workday count baru — yang ditampilkan adalah `usedDays` actual dari `leave_balances`.

## Limitations v1.5.0

- **Tidak ada auto sync libur nasional.** Admin manual input via endpoint POST.
- **Tidak ada regional holiday kompleks** (per-cabang/per-kota).
- **Tidak ada workday override per user** — workday settings tenant-level, sama untuk semua anggota.
- **Tidak ada half-day leave atau hourly leave.**
- **Tidak ada calendar import** dari file (.ics, CSV, Excel).
- **Tidak ada Google Calendar / Microsoft 365 integration.**
- **Tidak ada DELETE endpoint untuk holiday** — pakai PATCH `active=false`.
- **Tidak ada bulk create holiday.**
- **Workday calculation tidak dipakai untuk shift / late threshold / payroll.** Hanya mempengaruhi ANNUAL_LEAVE deduction.
- **Cross-year ANNUAL_LEAVE tetap ditolak** (sesuai pola v1.4.0). User harus split.
- **Tidak ada payroll integration.**
- **Tidak ada timezone kompleks** — pakai `LocalDate` server, konsisten dengan flow leave existing.
