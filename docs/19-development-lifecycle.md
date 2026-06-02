# Development Lifecycle

Dokumen ini menjelaskan lifecycle pengembangan Hadivo dan aturan main yang dipakai antar fase. Tujuannya supaya tiap rilis konsisten, aman, dan punya jejak yang bisa dilacak — bukan untuk menambah birokrasi.

Untuk checklist eksekusi rilis (command, tag, GitHub Release), lihat [`docs/20-release-checklist.md`](20-release-checklist.md).

## A. Overview

Hadivo memakai lifecycle tujuh tahap:

```
Planning → Analysis → Implementation → Review → QA → Release → Stabilization
```

Tiap fase fitur (mis. `v1.2.0 Shift & Flexible Schedule`, `v1.3.0 Correction Apply Engine`) menjalani semua tahap di atas. Tahap Stabilization umumnya jadi rilis patch (mis. `v1.2.1`, `v1.3.1`) berisi QA guide, Postman collection, dan perbaikan kecil tanpa perubahan endpoint/schema.

Lifecycle ini bukan model air terjun ketat. Iterasi di dalam satu fase (analysis ulang, refactor kecil saat implementation) wajar. Yang tidak boleh adalah melewati Review/QA hanya karena perubahan terasa "kecil".

## B. Planning

Sebelum coding, setiap fase wajib punya dokumen rencana yang memuat:

- **Versi target** — `vX.Y.Z` mengikuti konvensi semver yang dipakai repo.
- **Nama fitur/fase** — singkat, deskriptif, dipakai di tag dan release title.
- **Tujuan** — satu paragraf jelas, bukan jargon.
- **Masalah yang diselesaikan** — apa yang sebelumnya menyakitkan / tidak bisa dilakukan.
- **Scope** — daftar concrete deliverable per area (backend / web / mobile / docs / Postman / CI).
- **Non-goals / batasan** — apa yang sengaja tidak dikerjakan di fase ini.
- **Risiko** — teknis, security, data, kompatibilitas, CI.
- **Dampak ke backend / web / mobile / docs** — minimal satu baris per area, walau "tidak ada perubahan".
- **Acceptance criteria** — kondisi yang harus terpenuhi sebelum tag dibuat.

Planning ini bisa dituangkan di issue, dokumen internal, atau ringkasan di prompt fase. Yang penting bisa dirujuk kembali saat Review/QA.

## C. Analysis

Setelah planning disetujui, lakukan analisis dampak sebelum menyentuh code. Checklist minimum:

- **Module existing** — modul Spring Boot mana yang akan disentuh, dan modul mana yang harus tetap utuh.
- **Database / migration impact** — perlu migration baru? Bila ya, urutannya benar (`V<N>__...sql`) dan tidak merusak data existing.
- **API contract impact** — endpoint baru, field tambahan, atau perubahan response. Backward compatibility wajib dipertimbangkan.
- **Web impact** — halaman, route, atau komponen yang berubah; perlu screenshot baru atau tidak.
- **Mobile impact** — layar, navigasi, atau parser response.
- **Security impact** — tenant isolation, role guard, audit log baru, data sensitif di response/log.
- **Audit / logging impact** — action baru yang perlu didaftarkan, atau metadata baru.
- **Notification impact** — event baru, template baru, atau perubahan provider.
- **Reporting / export impact** — kolom baru di CSV/Excel/PDF dan dampaknya ke konsumen export.
- **Test impact** — unit test, integration test, mobile widget test, atau backend integration baru.
- **CI compatibility risk** — apakah perubahan butuh tool/version yang belum ada di workflow.
- **Rollback / stabilization concern** — jika fitur sensitif (payment, correction, leave), rencanakan patch stabilization terpisah.

Hasil analisis tidak perlu dokumen formal — ringkasan di komentar PR / prompt sudah cukup. Yang penting tiap item di atas sudah dipikirkan.

## D. Implementation Rules

Saat coding:

- **Jangan keluar scope.** Refactor besar di luar fase ditunda atau dipisah PR.
- **Jangan commit otomatis** tanpa konfirmasi user, walau test hijau.
- **Jangan hardcode secret** — gunakan env var dan `.env.example` sebagai placeholder.
- **Jangan commit `.env`, API key, service account, atau credential apapun.**
- **Jangan buat endpoint debug yang aktif di production** (mis. dump state, reset DB, override role).
- **Jangan buat migration yang merusak data existing** — kolom baru harus nullable atau punya default; jangan drop kolom tanpa rencana migrasi data.
- **Jaga tenant isolation.** Setiap query tenant-scoped wajib filter `tenant_id`. Endpoint wajib lewat membership guard.
- **Jaga backward compatibility response** kalau memungkinkan — tambah field opsional, jangan ganti tipe field existing.
- **Update docs** kalau behavior atau API berubah.
- **Update screenshot web** kalau UI berubah (`npm run screenshots`).
- **Update Postman collection** kalau API contract berubah.

## E. Review Checklist

Setelah coding selesai, jalankan review berikut sebelum lanjut ke QA:

- `git status --short` — tidak ada file tak terduga (build artifact, `.env`, dump).
- **Migration order check** — file migration urut `V<N>__...sql` tanpa lompat nomor.
- **Secret scan** — cek diff terhadap pola key produksi (lihat section I).
- **Backend test** — `./gradlew clean test` hijau (kalau backend disentuh).
- **Web lint / build** — `npm run lint` dan `npm run build` hijau (kalau web disentuh).
- **Web screenshots** — regenerate kalau UI berubah.
- **Mobile analyze / test** — `flutter analyze` dan `flutter test` hijau (kalau mobile disentuh).
- **Postman JSON parse** — collection valid JSON (kalau berubah).
- `git diff --check` — tidak ada whitespace error atau conflict marker tersisa.
- **AI attribution scan** — tidak ada frasa terlarang seperti "generated by AI", "created by ChatGPT", "Claude", "Codex", "Copilot", atau auto-attribution lain di file yang akan di-commit.
- **Review binary screenshots** — pastikan file PNG yang diubah memang screenshot baru, bukan file korup.

## F. QA Checklist

QA minimum sebelum release:

- **Happy path** — alur normal end-to-end via Postman atau UI.
- **Negative path** — input invalid, validasi backend, error message user-friendly.
- **Cross-tenant access** — request dengan token tenant A ke resource tenant B harus ditolak.
- **Role access** — endpoint role-restricted ditolak untuk role lain.
- **Audit log** — action baru tercatat dengan metadata yang sesuai dan tidak memuat secret.
- **Notification failure tolerance** — kegagalan provider notifikasi tidak menggagalkan flow utama (attendance, leave, payment).
- **Report / export impact** — kolom baru muncul di CSV / Excel / PDF dan tidak merusak konsumen lama.
- **Mobile impact** — kalau ada, uji minimal di emulator (login, alur fitur, logout).
- **Idempotency** — webhook payment, apply correction, dan endpoint sensitif lain harus idempotent (re-trigger tidak menggandakan efek).
- **Backward compatibility** — client lama tidak crash terhadap response baru.

Hasil QA dituangkan sebagai QA guide kalau fitur sensitif (lihat `docs/14-payment-qa-guide.md`, `docs/17-leave-qa-guide.md`, `docs/18-correction-qa-guide.md` sebagai contoh).

## G. Release Checklist

Tahap rilis dieksekusi mengikuti [`docs/20-release-checklist.md`](20-release-checklist.md). Ringkas:

- Commit rapi dengan prefix `feat:` / `fix:` / `chore:` / `docs:` / `test:`.
- Push ke `main`.
- Backend CI, Web CI, dan Mobile CI hijau di run terbaru.
- Tag versi `vX.Y.Z` dengan annotated message.
- GitHub Release dibuat dengan title dan body yang jelas, jujur soal limitasi.
- CHANGELOG.md update dengan entry versi yang dirilis.
- README / docs link update kalau perlu.

**CHANGELOG harus konsisten dengan GitHub Release.** Bila release pernah dibuat di GitHub tapi belum tertulis di CHANGELOG, backfill entry sebagai perubahan docs-only di rilis berikutnya. Jangan menulis ulang entry release yang sudah dipublikasi; cukup tambahkan baris yang hilang dengan tanggal dan scope yang benar. Begitu juga sebaliknya: bila CHANGELOG sudah punya entry tapi tag/GitHub Release belum ada, buat tag dan release segera atau hapus entry sampai siap.

## H. Stabilization Policy

Hadivo memakai pola dua-tahap untuk fitur besar:

- **Versi minor (atau major) untuk fitur besar** — mis. `v1.2.0` Shift & Flexible Schedule, `v1.3.0` Correction Apply Engine.
- **Versi patch untuk QA / stabilization** — mis. `v1.2.1` Leave QA Stabilization, `v1.3.1` Correction QA Stabilization.

Setelah fitur sensitif seperti payment, attendance correction, atau leave request, **wajib pertimbangkan rilis patch stabilization** sebelum lanjut ke fitur besar berikutnya. Patch ini berisi QA guide, Postman collection update, integration test tambahan, dan perbaikan dokumentasi — tanpa perubahan endpoint, schema, atau business logic.

Contoh pola yang sudah dipakai di repo:

| Fitur | Patch stabilization |
| --- | --- |
| `v1.0.0` Payment Foundation | `v1.0.1` Payment QA Stabilization |
| `v1.2.0` Leave / Permission Request | `v1.2.1` Leave QA Stabilization |
| `v1.3.0` Correction Apply Engine | `v1.3.1` Correction QA Stabilization |

Patch stabilization boleh dilewati kalau fitur memang sederhana dan QA sudah komprehensif sejak rilis minor. Keputusan ini ditulis eksplisit di planning fase berikutnya.

## I. Security & Secret Safety

File dan key berikut **tidak boleh** masuk ke commit:

- `.env` (asli, bukan `.env.example`).
- API key apapun: `MIDTRANS_SERVER_KEY`, `MIDTRANS_CLIENT_KEY`, `RESEND_API_KEY`.
- JWT secret produksi (`JWT_SECRET` non-test).
- Firebase service account JSON.
- `google-services.json` produksi, `GoogleService-Info.plist` produksi.
- FCM token, refresh token, atau access token live.
- GitHub Personal Access Token (`ghp_...`, `github_pat_...`).
- Private key dalam bentuk apapun (`-----BEGIN PRIVATE KEY-----`, `private_key`).

`.env.example` di repo hanya berisi placeholder (`...`, `your-value-here`, atau string kosong). Setiap variabel rahasia harus muncul di `.env.example` tanpa value nyata, supaya developer baru tahu key apa yang perlu di-set lokal.

Bila secret tidak sengaja ter-commit, **jangan hanya hapus di commit berikutnya** — rotasi key tersebut di provider, lalu pertimbangkan history rewrite kalau memang sensitif.

## J. Recommended Prompt Pattern

Untuk fase baru yang dikerjakan dengan bantuan AI assistant, gunakan template prompt berikut sebagai kerangka:

```
Nama fase: <nama deskriptif>
Versi target: vX.Y.Z

Kondisi project saat ini:
- versi terakhir yang sudah release
- CI hijau / merah
- git status --short bersih sebelum mulai

Tujuan:
<satu paragraf jelas>

Scope utama:
1. ...
2. ...

Non-goals:
- jangan ubah <area>
- jangan tambah <kategori>

Sebelum coding:
1. Analisis README, docs, CHANGELOG, CI workflow.
2. Tampilkan rencana implementasi singkat.
3. Tunggu persetujuan sebelum eksekusi.

Validasi wajib:
- git diff --check
- secret scan
- AI attribution forbidden phrase scan
- git status --short

Jangan commit otomatis.

Output ringkasan:
- file yang dibuat/diubah
- isi utama tiap file
- hasil validasi
- apakah aman untuk commit
```

Template ini membantu menjaga lifecycle tetap konsisten antar fase, terutama untuk tahap Planning, Analysis, Review, dan Release.
