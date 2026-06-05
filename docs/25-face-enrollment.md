# Face Recognition Enrollment Foundation (v1.6.0)

## Scope

v1.6.0 adalah **foundation only**. Yang tersedia:

- Tabel `user_face_profiles` per (tenant, user).
- Endpoint enroll / reset / get profil wajah dengan consent eksplisit.
- Penyimpanan file enrollment di local disk dengan referensi relatif di DB.
- Reset oleh admin yang membersihkan referensi DB dan mencoba menghapus file lokal.
- Kolom Wajah di halaman web `Anggota`.
- Layar `Daftar Wajah` di mobile dengan picker via `image_picker`.

Yang **belum** tersedia dan tidak dilakukan di versi ini:

- Face detection. Validasi gambar di v1.6.0 hanya cek format (JPEG / PNG magic bytes) dan ukuran. Ini bukan pengecekan wajah.
- Face matching. Backend tidak membandingkan foto enrollment dengan foto clock-in / clock-out.
- Embedding engine.
- Liveness detection.
- Anti-spoofing.
- Endpoint admin approve / reject enrollment.
- Gating clock-in / clock-out berdasarkan status enrollment.

Clock-in / clock-out flow, payment, leave, holiday, dan attendance correction tidak diubah.

## Schema

Migration `V12__add_face_enrollment_foundation.sql`:

```sql
create table user_face_profiles (
    id                   uuid primary key default gen_random_uuid(),
    tenant_id            uuid not null references tenants(id) on delete cascade,
    user_id              uuid not null references users(id) on delete cascade,
    enrollment_status    varchar(20) not null default 'PENDING',
    consent_given        boolean not null default false,
    consent_given_at     timestamptz,
    image_reference      varchar(255),
    embedding_reference  varchar(255),
    enrolled_at          timestamptz,
    reset_at             timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint uniq_face_profiles_tenant_user unique (tenant_id, user_id)
);
```

`embedding_reference` disiapkan untuk fase lanjutan, tapi v1.6.0 selalu menyimpan `null`.

## Enrollment status

| Status   | Arti                                                                                                          |
|----------|---------------------------------------------------------------------------------------------------------------|
| PENDING  | Belum ada enrollment, atau profil baru dibuat tapi belum berhasil store image                                 |
| ACTIVE   | Foto enrollment tersimpan dan consent sudah diberikan. **Bukan** berarti pencocokan wajah aktif.              |
| RESET    | Admin sudah me-reset profil. Referensi DB dibersihkan dan file lokal di-best-effort delete.                   |

UI web dan mobile harus konsisten dengan definisi di atas: `ACTIVE` tidak boleh disampaikan ke user dengan kata-kata yang menyiratkan matching sudah aktif.

## Endpoint

Semua endpoint berada di scope tenant.

```
GET   /api/v1/tenants/{tenantId}/members/{userId}/face-profile
POST  /api/v1/tenants/{tenantId}/members/{userId}/face-profile/enroll
POST  /api/v1/tenants/{tenantId}/members/{userId}/face-profile/reset
```

- `GET` boleh dipanggil oleh user sendiri atau oleh `TENANT_ADMIN` / `SUPER_ADMIN`. Jika belum ada profil, mengembalikan view PENDING kosong (`imageStored=false`).
- `POST .../enroll` body:

  ```json
  {
    "imageBase64": "<base64 JPEG atau PNG>",
    "consentGiven": true
  }
  ```

  Tanpa `consentGiven: true` ditolak `VALIDATION_FAILED`.
- `POST .../reset` admin-only. Tidak butuh body.

### Response shape

Setiap endpoint mengembalikan `FaceProfileView` dengan field minimal demi privasi:

```json
{
  "profileId": "uuid",
  "enrollmentStatus": "ACTIVE | PENDING | RESET",
  "consentGiven": true,
  "imageStored": true,
  "enrolledAt": "ISO-8601 atau null",
  "resetAt": "ISO-8601 atau null",
  "updatedAt": "ISO-8601",
  "message": "string"
}
```

Field yang **tidak** dikembalikan ke frontend / mobile:

- `imageReference` (path relatif di disk).
- `embeddingReference`.
- Absolute path.
- Base64 yang dikirim ke endpoint enroll.

## Image validation

Validasi pada `POST .../enroll`:

1. `imageBase64` wajib non-blank.
2. Base64 size cap (≈ 7.5 juta karakter ≈ ±5 MB binary) dicek sebelum decode untuk mencegah DoS dengan payload sangat besar.
3. Magic bytes:
   - JPEG: `FF D8 FF`
   - PNG: `89 50 4E 47`
4. Format lain (BMP, WEBP, GIF, HEIC, dll.) ditolak `VALIDATION_FAILED`.

Validasi ini **bukan** validasi wajah. v1.6.0 tidak menjalankan face detection, matching, liveness, atau anti-spoofing.

## Local storage

- Konfigurasi: `hadivo.face.storage-dir` (env `HADIVO_FACE_STORAGE_DIR`), default `backend/storage/face`.
- Layout: `<storage-dir>/<tenantId>/<userId>-<timestampUtc>.<jpg|png>`.
- Yang disimpan di DB hanya path **relatif** terhadap storage root. Absolute path tidak pernah masuk ke DB, response, atau audit metadata.
- Direktori `backend/storage/` masuk `.gitignore`. **Jangan** commit sample image, biometric data, atau base64 sample besar.

### Reset cleanup behavior

Saat admin memanggil `POST .../reset`:

1. `image_reference` dan `embedding_reference` di DB di-set `null`.
2. `enrollment_status` menjadi `RESET`. `reset_at` dicatat.
3. Backend mencoba menghapus file di disk yang dirujuk oleh `image_reference` sebelumnya. Jika gagal (file sudah tidak ada, permission, dll.), warning di-log tanpa membocorkan path lengkap. Endpoint tetap sukses; cleanup bersifat best-effort.
4. Path traversal dicegah: resolved path harus berada di bawah storage root.

Re-enrollment setelah reset menghasilkan referensi file baru dan menghapus file lama.

## Audit

Action baru: `FACE_PROFILE_ENROLLED`, `FACE_PROFILE_RESET`.

Metadata yang **disimpan** di audit log:

- `userId`
- `enrollmentStatus`
- `consentGiven`
- `imageStored`
- `profileId`

Metadata yang **tidak** disimpan di audit log:

- `imageBase64`
- `imageReference`
- `embeddingReference`
- Absolute path
- Sample biometric apa pun

## Web

Halaman `/members` (admin) menambahkan kolom `Wajah` per anggota. Kolom menampilkan:

- Badge status (`Terdaftar`, `Direset`, `Belum enroll`).
- Badge `Foto tersimpan` saat `imageStored=true`.
- Catatan: *Status ACTIVE berarti foto dan persetujuan tercatat. Pencocokan wajah belum aktif.*
- Tombol `Reset Wajah` (admin only) saat foto tersimpan.

## Mobile

Layar `Daftar Wajah` (`/profile/face`):

- Menampilkan status enrollment terkini.
- Picker via `image_picker` (kamera atau galeri).
- Checkbox consent wajib dicentang sebelum submit.
- Setelah submit, status di profile screen otomatis di-refresh.

Permission strings yang ditambahkan di iOS `Info.plist`:

- `NSCameraUsageDescription` — kamera untuk foto enrollment.
- `NSPhotoLibraryUsageDescription` — galeri untuk memilih foto.

Android: tidak ada perubahan manifest selain plugin auto-merge dari `image_picker`. Absensi tidak dipengaruhi oleh status enrollment.

## QA & validasi

Backend (`./gradlew clean test`):

- `enroll without consent rejected`
- `enroll rejects non-jpeg-non-png magic bytes`
- `enroll response does not expose imageReference or path`
- `admin reset clears references and deletes local file`
- `employee cannot reset face profile`
- `re-enroll after reset creates new stored image reference`
- `re-enroll deletes previous image file`
- `audit metadata does not expose base64 or path`
- `cross user enroll forbidden for employee`
- `get face profile returns pending for unenrolled user`

Mobile (`flutter test`): unit test `FaceProfile.fromJson` untuk ketiga status. Tidak ada test yang memerlukan kamera real.

## Limitations

- Tidak ada face detection, matching, liveness, anti-spoofing, atau embedding engine.
- Endpoint admin approve / reject enrollment belum tersedia.
- Quota / size cap masih tunggal global, bukan per tenant.
- Storage selalu local disk; object storage / S3 belum didukung.
