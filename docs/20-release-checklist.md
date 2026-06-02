# Release Checklist

Checklist eksekusi untuk merilis versi baru Hadivo. Pakai dokumen ini sebagai daftar konkret yang dijalankan tiap rilis. Untuk konteks lifecycle yang lebih luas (Planning, Analysis, QA), lihat [`docs/19-development-lifecycle.md`](19-development-lifecycle.md).

## A. Pre-Release Checklist

Sebelum mulai fase rilis:

- `git status --short` bersih sebelum mulai fase. Tidak boleh ada perubahan belum di-commit yang nyangkut dari sesi sebelumnya.
- Rencana implementasi sudah disetujui (lihat lifecycle section B Planning).
- Tidak ada secret asli (API key, service account, `.env`) di file yang akan di-commit.
- Migration berurut tanpa lompat nomor (`V<N>__...sql`).
- Docs sudah update untuk perubahan behavior / API / UI / mobile.
- Test sudah update kalau backend / web / mobile disentuh.
- **CHANGELOG sudah punya entry untuk versi yang akan dirilis.** Tanpa entry CHANGELOG, jangan buat tag.
- **GitHub Release dan CHANGELOG tidak boleh saling bertentangan.** Sebelum tag dibuat, pastikan judul, scope, dan limitasi di CHANGELOG entry konsisten dengan body GitHub Release yang akan dipublikasi.

## B. Validation Commands

Jalankan perintah berikut dari root repo (atau folder yang ditunjukkan), sesuai area yang disentuh.

### Backend

```
cd backend
./gradlew clean test
```

Di Windows PowerShell pakai `.\gradlew.bat clean test`.

### Web

```
cd web
npm run lint
npm run build
npm run screenshots   # jalankan jika UI berubah
```

### Mobile

```
cd mobile
flutter pub get
dart format lib test
flutter analyze
flutter test
```

### Secret scan

```
git diff | grep -Ei "MIDTRANS_SERVER_KEY=.+[A-Za-z0-9]|MIDTRANS_CLIENT_KEY=.+[A-Za-z0-9]|RESEND_API_KEY=.+[A-Za-z0-9]|private_key|ghp_|github_pat_|firebase-adminsdk|google-services.json"
```

Hasil yang aman: **tidak ada baris yang cocok**. Bila ada, hentikan rilis dan rotasi key terkait.

### Migration check

```
ls backend/src/main/resources/db/migration
```

Pastikan file urut `V1__...sql`, `V2__...sql`, dan seterusnya tanpa lompat nomor atau duplikasi versi.

## C. Commit Checklist

- `git add <path>` — tambahkan file relevan secara eksplisit. Hindari `git add -A` / `git add .` supaya artifact tak sengaja tidak ikut.
- `git status --short` setelah staging — verifikasi hanya file yang dimaksud yang masuk.
- Commit message memakai prefix konvensional:
  - `feat:` fitur baru
  - `fix:` perbaikan bug
  - `chore:` housekeeping / config / stabilization
  - `docs:` perubahan dokumentasi saja
  - `test:` penambahan/perbaikan test saja
- Push ke `main` (atau buka PR, sesuai workflow yang berlaku).

## D. GitHub Actions Checklist

Setelah push:

- **Backend CI** hijau di run terbaru untuk commit yang akan di-tag.
- **Web CI** hijau di run terbaru.
- **Mobile CI** hijau di run terbaru.
- Run lama yang merah boleh diabaikan **jika run terbaru sudah hijau** untuk semua workflow di atas.

Jangan buat tag rilis kalau ada CI relevan yang masih merah atau pending.

## E. Tag & Release Checklist

- Buat annotated tag:

  ```
  git tag -a vX.Y.Z -m "Hadivo vX.Y.Z - Release Name"
  git push origin vX.Y.Z
  ```

- Buat GitHub Release dengan:
  - **Title** — format `Hadivo vX.Y.Z - Release Name`, konsisten dengan annotated tag.
  - **Body** — ringkasan scope per area (Backend / Web Dashboard / Mobile / Docs), notes/limitations yang jujur.
  - Cantumkan limitasi yang sudah diketahui (mis. "no revert endpoint", "mock provider default"). **Jangan klaim fitur yang belum ada.**
- **Konsistensi CHANGELOG ↔ Release** — verifikasi sekali lagi:
  - Versi di tag, judul GitHub Release, dan heading CHANGELOG identik.
  - Daftar perubahan utama di body release tercermin di CHANGELOG (atau sebaliknya).
  - Limitasi yang ditulis di release juga ada di CHANGELOG bila relevan.

## F. Post-Release Checklist

- `git status --short` kosong setelah push tag.
- GitHub Release muncul di halaman Releases repo.
- README / CHANGELOG sesuai dengan kondisi terkini (link, versi terbaru, screenshot kalau relevan).
- Lanjut ke patch **stabilization** kalau fitur yang baru dirilis termasuk sensitif (payment / correction / leave / auth / migration). Pola stabilization dijelaskan di [`docs/19-development-lifecycle.md`](19-development-lifecycle.md) section H.

Bila ditemukan inkonsistensi antara CHANGELOG dan GitHub Release setelah rilis (mis. CHANGELOG belum sempat di-update), backfill sebagai perubahan **docs-only** di rilis berikutnya — jangan ubah tag/release yang sudah dipublikasi.
