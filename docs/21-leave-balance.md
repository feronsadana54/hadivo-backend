# Leave Balance / Quota Foundation

Dokumen ini menjelaskan fondasi saldo cuti tahunan Hadivo v1.4.0. Tujuannya: tenant bisa mengatur jatah cuti per user, dan saldo otomatis berkurang saat `ANNUAL_LEAVE` disetujui — dengan jejak audit yang lengkap.

Untuk policy umum leave/permission lihat [`docs/16-leave-permission.md`](16-leave-permission.md). Untuk QA leave secara umum lihat [`docs/17-leave-qa-guide.md`](17-leave-qa-guide.md). Untuk QA manual khusus leave balance (Postman flow, SQL verifikasi, troubleshooting) lihat [`docs/22-leave-balance-qa-guide.md`](22-leave-balance-qa-guide.md).

## Konsep

Tiga tabel utama:

- **`leave_policies`** — satu policy per tenant. Berisi kuota cuti tahunan default dan flag opsional yang menentukan jenis pengajuan lain (sick / permission / business_trip) ikut mengurangi saldo atau tidak. Default v1.4.0: hanya `ANNUAL_LEAVE` yang mengurangi saldo.
- **`leave_balances`** — saldo per user per tahun. Dibuat lazy (saat pertama kali diakses atau saat dibutuhkan untuk deduct).
- **`leave_balance_ledgers`** — riwayat setiap perubahan saldo (`INITIAL`, `DEDUCT`, `ADJUST`, `RESTORE`). Tidak boleh ada perubahan saldo tanpa ledger.

Rumus saldo:

```
remaining_days = annual_quota_days + adjusted_days - used_days
```

Saldo dihitung ulang setiap kali ada perubahan (`recomputeRemaining()`), dengan skala dua angka di belakang koma.

## Endpoint

### Policy

| Method | Path | Akses |
| --- | --- | --- |
| `GET` | `/api/v1/tenants/{tenantId}/leave-policy` | semua member tenant (read) |
| `PUT` | `/api/v1/tenants/{tenantId}/leave-policy` | `TENANT_ADMIN`, `SUPER_ADMIN` |

GET pertama kali untuk tenant baru akan membuat policy default (`annualLeaveQuotaDays=12`, semua boolean `*_requires_balance=false`, `active=true`).

PUT body (semua field opsional):

```json
{
  "name": "Default",
  "annualLeaveQuotaDays": 15,
  "sickLeaveRequiresBalance": false,
  "permissionRequiresBalance": false,
  "businessTripRequiresBalance": false,
  "active": true
}
```

Validasi: `annualLeaveQuotaDays` harus `0 ≤ x ≤ 365`. Audit `LEAVE_POLICY_UPDATED` mencatat `changedFields`.

### Balance

| Method | Path | Akses |
| --- | --- | --- |
| `GET` | `/api/v1/tenants/{tenantId}/leave-balances?year=YYYY` | `TENANT_ADMIN`, `SUPER_ADMIN` |
| `GET` | `/api/v1/tenants/{tenantId}/members/{userId}/leave-balance?year=YYYY` | `TENANT_ADMIN` / `SUPER_ADMIN` (semua) / member tenant lain (hanya `userId == self`) |
| `POST` | `/api/v1/tenants/{tenantId}/members/{userId}/leave-balance/adjust` | `TENANT_ADMIN`, `SUPER_ADMIN` |

Parameter `year` opsional. Default = tahun berjalan.

Adjust body:

```json
{
  "year": 2026,
  "days": 2.0,
  "note": "Bonus tahunan"
}
```

Validasi adjust:

- `days` tidak boleh nol.
- `note` wajib.
- `year` ∈ `[currentYear - 1, currentYear + 1]`.
- Hasil `remaining_days` tidak boleh negatif (`< 0` → `VALIDATION_FAILED`).

## Annual leave deduction

Integrasi terjadi di `LeaveRequestService.review()` untuk transition `PENDING → APPROVED`:

1. Hanya request dengan `requestType == ANNUAL_LEAVE`.
2. `startYear` dan `endYear` harus sama → kalau berbeda, throw `VALIDATION_FAILED` dengan pesan _"Pengajuan cuti lintas tahun belum didukung."_. Status request tetap `PENDING`.
3. `days = ChronoUnit.DAYS.between(start, end) + 1` (kalender, inclusive).
4. Balance di-init lazy dari policy jika belum ada (ledger `INITIAL`).
5. Jika `remaining_days < days` → throw `VALIDATION_FAILED` dengan pesan _"Sisa cuti tahunan tidak mencukupi untuk pengajuan ini."_. Status request tetap `PENDING`.
6. Update `used_days`, recompute `remaining_days`. Tulis ledger `DEDUCT` dengan `leave_request_id`.
7. Audit `LEAVE_BALANCE_DEDUCTED` dengan metadata `userId`, `year`, `daysChanged`, `balanceBefore`, `balanceAfter`, `leaveRequestId`.

Karena semua langkah berjalan di transaksi parent `review()`, kegagalan deduct otomatis rollback approve — tidak akan ada kondisi "status `APPROVED` tapi saldo belum berkurang".

Untuk `SICK`, `PERMISSION`, `BUSINESS_TRIP`, dan `ATTENDANCE_CORRECTION`: tidak ada deduct di v1.4.0, walau policy `*_requires_balance` di tabel sudah disiapkan untuk fase berikutnya.

## Idempotency

Tiga lapis:

1. **Status guard di LeaveRequestService** — `review()` menolak transition kalau status bukan `PENDING`, sehingga double-approve mustahil via API.
2. **Service guard** — `deductForApproval` cek `existsByLeaveRequestIdAndChangeType(leaveRequestId, DEDUCT)` sebelum menulis. Kalau ada, skip (log info).
3. **DB guard** — partial UNIQUE index `(leave_request_id) WHERE change_type='DEDUCT'`. Kalau lapis 1 dan 2 entah bagaimana lewat, INSERT gagal dan transaksi rollback.

## Audit actions

- `LEAVE_POLICY_UPDATED` — metadata `changedFields`.
- `LEAVE_BALANCE_INITIALIZED` — metadata `userId`, `year`, `annualQuotaDays`.
- `LEAVE_BALANCE_ADJUSTED` — metadata `userId`, `year`, `daysChanged`, `balanceBefore`, `balanceAfter`.
- `LEAVE_BALANCE_DEDUCTED` — metadata `userId`, `year`, `daysChanged`, `balanceBefore`, `balanceAfter`, `leaveRequestId`.

Audit logger memakai `Propagation.REQUIRES_NEW` jadi setiap event commit independen.

## Web Dashboard

- **`/leave-balances`** (`TENANT_ADMIN` / `SUPER_ADMIN`) — tabel saldo per member untuk tahun terpilih. Kolom: anggota, tahun, kuota, terpakai, penyesuaian, sisa (badge), aksi.
- Filter: tahun (range `currentYear-1`..`currentYear+1`), search nama/email.
- Tombol **Sesuaikan** membuka modal dengan field tahun, hari (boleh negatif), dan catatan wajib.
- Badge sisa: hijau (`>=3`), kuning (`<3`), merah (`<=0`).
- Empty state: _"Belum ada data saldo cuti."_.

Halaman `/leave-requests` juga menampilkan jumlah hari untuk row `ANNUAL_LEAVE` di kolom Periode.

## Mobile App

Profile screen menampilkan card **"Sisa Cuti Tahunan"** read-only: `Sisa N hari dari Q hari` + `Tahun YYYY • Terpakai X hari`. Jika fetch gagal atau user tidak punya saldo, card menampilkan _"Belum tersedia"_ tanpa crash.

Mobile tidak menyediakan adjustment, edit policy, atau ledger history — admin pakai web.

## Limitations v1.4.0

- **Tidak ada accrual bulanan / kuartalan / pro-rata.** Kuota tahunan adalah angka tetap dari policy.
- **Tidak ada carry-forward** antar tahun. Saldo tahun N+1 dibuat fresh dengan kuota baru.
- **Tidak ada half-day leave** dan **tidak ada hourly leave**. Hari dihitung penuh inclusive `endDate - startDate + 1`.
- **Tidak ada holiday calendar / workday-only calculation**. Saat liburan nasional tetap dihitung sebagai hari pengurang saldo.
- **Tidak ada cancel approved leave / restore balance**. Cancel hanya berlaku untuk request `PENDING` (mengikuti `LeaveRequestService.cancel` existing). Ledger `RESTORE` sudah dialokasikan di enum dan migration untuk fase berikutnya, tapi belum ada flow yang menulisnya.
- **Tidak ada cross-year leave**. Request `ANNUAL_LEAVE` yang melewati pergantian tahun ditolak. User harus split jadi dua request.
- **Tidak ada payroll integration**.
- **Tidak ada attachment upload** untuk pengajuan cuti.
- **Reviewer hierarchy tidak berubah** — masih `TENANT_ADMIN` / `SUPER_ADMIN`. Tidak ada manager-tier approval.
- **Saldo tidak boleh negatif** lewat adjust admin. Kalau perlu correction yang menghasilkan negatif, lakukan via audit di luar API dan dokumentasikan.

Future plan terkait dapat menambah action `LEAVE_BALANCE_RESTORE` dengan handler yang membaca ledger `DEDUCT` untuk leave_request tertentu, melakukan reverse, dan menulis ledger baru. Implementasi tidak masuk v1.4.0 dan butuh keputusan eksplisit terkait skema cancel approved leave.
