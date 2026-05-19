# Face verification flow

## Status Fase 1

Verifikasi wajah masih **demo**. `DemoFaceVerifier` mengembalikan true kalau `faceImageBase64` tidak kosong dan panjangnya wajar. Tujuannya untuk membuktikan end-to-end flow request → reject pada `FACE_MISMATCH` → log attempt.

## Kontrak

```kotlin
interface FaceVerifier {
    fun verify(userId: UUID, imageBase64: String?): Boolean
}
```

Bean default-nya `DemoFaceVerifier`. Untuk Fase 2 cukup ganti `@Component` ke implementasi baru (mis. `ProviderFaceVerifier` yang memanggil layanan ML), atau jadikan bean primary.

## Trigger

`AttendanceService` memanggil `verify` hanya kalau `tenant_attendance_settings.require_face_clock_in` (atau `..._clock_out`) bernilai `true`. Default kedua flag = false agar demo bisa berjalan tanpa kirim gambar.

## Limitation Fase 1

- Tidak ada enrollment dataset per user.
- Tidak ada penyimpanan embedding.
- Tidak ada anti-spoofing.

## Roadmap Fase 2

- Tambah tabel `user_face_embeddings`.
- Tambah endpoint enroll wajah saat onboarding member.
- Ganti `FaceVerifier` dengan implementasi yang menghitung cosine similarity terhadap embedding tersimpan.
