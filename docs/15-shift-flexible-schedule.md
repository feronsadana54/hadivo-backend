# Shift & flexible schedule

Fase v1.1.0 menambahkan fondasi shift sederhana untuk tenant yang membutuhkan jam kerja atau jam masuk berbeda per anggota.

## Konsep

### Shift template

Shift template adalah pola jadwal tenant. Field utama:

- `name`
- `startTime`
- `endTime`
- `lateThresholdMinutes`
- `allowsOvertime`
- `active`

Shift boleh melewati tengah malam. Contoh: `22:00` sampai `06:00` dianggap overnight shift.

### Assignment anggota

Assignment menghubungkan satu anggota tenant ke satu shift template untuk periode tertentu:

- `effectiveFrom`
- `effectiveTo` opsional
- `active`

Assignment aktif untuk anggota yang sama tidak boleh overlap. Validasi overlap dilakukan di service backend. Untuk v1.1.0, sistem tidak membuat roster otomatis, shift swap, atau approval overtime kompleks.

## Fallback ke tenant attendance settings

Jika anggota belum memiliki assignment shift aktif pada tanggal absensi, backend tetap memakai `tenant_attendance_settings`:

- `workStartTime`
- `workEndTime`
- `lateThresholdMinutes`
- `allowLateClockIn`
- timezone tenant

Fallback ini menjaga tenant existing tetap berjalan tanpa migrasi data operasional.

## Late calculation

Saat clock-in, backend resolve jadwal harian anggota:

1. Cari assignment aktif untuk tanggal absensi.
2. Jika ada, gunakan `startTime` dan `lateThresholdMinutes` dari shift.
3. Jika tidak ada, gunakan attendance settings tenant.

Status `LATE` dihitung dari:

```text
scheduled start time + late threshold minutes
```

Jika `allowLateClockIn=false`, clock-in yang melewati batas tersebut tetap ditolak seperti flow sebelumnya.

## Overnight shift

Overnight shift didukung untuk skenario sederhana:

- Shift `22:00-06:00` pada tanggal 5 Januari memiliki attendance date 5 Januari.
- Clock-out pada 6 Januari pagi tetap dicari sebagai bagian dari shift tanggal 5 Januari selama jam lokal belum melewati `endTime`.
- Snapshot shift disimpan di `attendance_records` saat clock-in agar report tetap konsisten jika template shift berubah setelahnya.

Limitasi MVP:

- Belum ada grace window sebelum start shift.
- Belum ada roster generator.
- Belum ada split shift dalam satu hari.
- Belum ada holiday calendar kompleks.
- Belum ada leave management.
- Belum ada payroll.
- Belum ada approval overtime.

## Endpoint

Shift template:

```http
GET /api/v1/tenants/{tenantId}/shifts
POST /api/v1/tenants/{tenantId}/shifts
PATCH /api/v1/tenants/{tenantId}/shifts/{shiftId}
```

Assignment anggota:

```http
GET /api/v1/tenants/{tenantId}/members/{userId}/shift-assignments
POST /api/v1/tenants/{tenantId}/members/{userId}/shift-assignments
PATCH /api/v1/tenants/{tenantId}/members/{userId}/shift-assignments/{assignmentId}
```

Access policy:

- `TENANT_ADMIN` dan `SUPER_ADMIN` dapat membuat/mengubah shift dan assignment.
- `MANAGER` dapat membaca shift template.
- `EMPLOYEE`, `STUDENT`, dan `PARENT` tidak dapat mengelola shift.
- Tenant isolation tetap memakai path `tenantId` dan membership guard.

## Report dan export

Daily report dan export attendance menambahkan snapshot jadwal:

- `shiftId`
- `shiftName`
- `scheduledStartTime`
- `scheduledEndTime`
- `lateThresholdMinutes`

CSV, Excel, dan PDF menambahkan kolom shift agar hasil report menunjukkan jadwal yang dipakai saat clock-in.

## Audit log

Action baru:

- `SHIFT_CREATED`
- `SHIFT_UPDATED`
- `SHIFT_ASSIGNMENT_CREATED`
- `SHIFT_ASSIGNMENT_UPDATED`

Audit metadata hanya menyimpan nama shift, waktu, status aktif, user id target assignment, dan periode assignment. Tidak ada secret atau credential yang disimpan.

## Web dashboard

Halaman `/shifts` menyediakan:

- daftar shift template;
- form buat/edit shift;
- indikator overnight shift;
- form assignment shift ke anggota;
- daftar assignment anggota terpilih.

Halaman `/members` menampilkan current shift jika anggota punya assignment aktif. Jika tidak ada, tampil sebagai jadwal tenant.
