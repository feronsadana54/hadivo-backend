# Panduan Penggunaan Mobile App

Dokumen ini ditujukan untuk user akhir Hadivo Mobile, terutama `EMPLOYEE` dan `STUDENT`. Panduan ini menjelaskan cara memakai aplikasi untuk login, melihat status absensi, clock-in, clock-out, melihat riwayat, dan logout.

## Sebelum Mulai

Pastikan hal berikut sudah siap:

- Aplikasi Hadivo Mobile sudah terpasang atau sedang berjalan di emulator/perangkat.
- Backend Hadivo aktif dan dapat diakses oleh aplikasi.
- User sudah memiliki akun dan terdaftar sebagai anggota tenant.
- Untuk demo lokal, gunakan akun:
  - `employee@hadivo.local` / `ChangeMe123!`
  - `student@hadivo.local` / `ChangeMe123!`

Untuk demo lokal, mode lokasi demo aktif secara default. Artinya aplikasi memakai koordinat lokasi demo tenant, sehingga user tidak perlu mengatur GPS emulator secara manual.

## Alur Singkat

1. Buka aplikasi Hadivo Mobile.
2. Login memakai email dan password.
3. Buka tab `Beranda`.
4. Tekan `Clock In` saat mulai hadir.
5. Pastikan status hari ini berubah dan jam masuk tampil.
6. Tekan `Clock Out` saat selesai.
7. Buka tab `Riwayat` untuk melihat absensi beberapa hari terakhir.
8. Buka tab `Profil` untuk melihat informasi akun atau logout.

## Login

1. Buka aplikasi.
2. Masukkan email.
3. Masukkan password.
4. Tekan tombol `Masuk`.

Jika login berhasil, aplikasi akan membuka halaman `Beranda`. Jika login gagal, aplikasi menampilkan pesan error seperti email/password tidak sesuai atau server belum dapat dihubungi.

## Beranda

Tab `Beranda` adalah halaman utama absensi harian. Halaman ini menampilkan:

- sapaan user;
- status absensi hari ini;
- tanggal absensi;
- jam masuk;
- jam keluar;
- durasi kerja/belajar;
- tombol aksi `Clock In`, `Clock Out`, atau status selesai.

Jika belum pernah clock-in pada hari itu, aplikasi menampilkan status `Belum clock-in` dan tombol `Clock In`.

## Clock In

Gunakan `Clock In` saat user mulai hadir.

1. Buka tab `Beranda`.
2. Pastikan status masih `Belum clock-in`.
3. Tekan tombol `Clock In`.
4. Tunggu sampai proses selesai.
5. Jika berhasil, aplikasi menampilkan pesan `Clock-in berhasil. Semoga harimu lancar.`

Setelah clock-in berhasil, halaman `Beranda` akan menampilkan jam masuk. Tombol utama berubah menjadi `Clock Out`.

Clock-in dapat gagal jika:

- user berada di luar radius lokasi absensi;
- user sudah clock-in hari ini;
- perangkat tidak valid;
- server/backend tidak dapat dihubungi;
- aturan tenant menolak clock-in terlambat.

## Clock Out

Gunakan `Clock Out` saat user selesai bekerja atau selesai kegiatan.

1. Buka tab `Beranda`.
2. Pastikan jam masuk sudah tampil.
3. Tekan tombol `Clock Out`.
4. Tunggu sampai proses selesai.
5. Jika berhasil, aplikasi menampilkan pesan `Clock-out berhasil. Terima kasih.`

Setelah clock-out berhasil, halaman `Beranda` menampilkan jam keluar dan durasi. Tombol utama berubah menjadi `Absensi hari ini selesai`.

Clock-out dapat gagal jika:

- user belum clock-in;
- user sudah clock-out hari ini;
- user berada di luar radius dan tenant tidak mengizinkan clock-out di luar radius;
- perangkat yang dipakai berbeda dari perangkat terdaftar;
- server/backend tidak dapat dihubungi.

## Riwayat

Tab `Riwayat` dipakai untuk melihat catatan absensi beberapa hari terakhir.

Di halaman ini user dapat melihat:

- tanggal absensi;
- status absensi;
- jam masuk;
- jam keluar;
- durasi.

Jika data belum muncul, tarik layar ke bawah untuk refresh. Jika server tidak dapat dihubungi, aplikasi menampilkan pesan bahwa riwayat belum bisa dimuat.

## Profil

Tab `Profil` menampilkan informasi akun yang sedang login.

Informasi yang tersedia:

- email user;
- status login;
- tenant demo;
- status mode lokasi demo.

Untuk keluar dari aplikasi:

1. Buka tab `Profil`.
2. Tekan tombol `Keluar`.
3. Aplikasi akan kembali ke halaman login.

## Pesan Error Umum

| Pesan | Arti | Tindakan user |
| --- | --- | --- |
| `Tidak dapat terhubung ke server` | Aplikasi tidak bisa menghubungi backend. | Pastikan backend berjalan dan koneksi benar. |
| `Anda berada di luar area absensi` | Lokasi user di luar radius tenant. | Dekatkan perangkat ke lokasi kantor/sekolah atau hubungi admin. |
| `Anda sudah melakukan clock-in hari ini` | Clock-in tidak bisa diulang pada hari yang sama. | Lanjutkan dengan clock-out saat selesai. |
| `Anda belum melakukan clock-in` | Clock-out dicoba sebelum clock-in. | Lakukan clock-in terlebih dahulu. |
| `Anda sudah melakukan clock-out hari ini` | Absensi hari ini sudah selesai. | Tidak perlu melakukan aksi lagi. |
| `Perangkat ini belum terdaftar untuk absensi` | Device berbeda dari device yang sudah dipercaya. | Hubungi admin tenant untuk reset perangkat. |
| `Sesi Anda sudah berakhir` | Access token sudah tidak valid. | Login ulang. |

## Device Binding

Hadivo Mobile menyimpan ID perangkat acak di secure storage. Perangkat pertama yang berhasil dipakai untuk absensi akan menjadi trusted device untuk user tersebut pada tenant tersebut.

Jika user mengganti perangkat atau menghapus lalu memasang ulang aplikasi, device ID bisa berubah. Jika backend menolak absensi karena perangkat berbeda, user perlu menghubungi admin tenant untuk reset perangkat.

## Catatan Demo Lokal

Dalam demo lokal:

- tenant demo memakai ID `11111111-1111-1111-1111-111111111111`;
- mode lokasi demo aktif secara default;
- koordinat demo adalah latitude `-6.2` dan longitude `106.816666`;
- akun demo dibuat otomatis oleh backend saat startup local/dev;
- push notification bersifat opsional dan tidak wajib untuk login atau absensi.

## Batasan Saat Ini

- Belum ada mode offline.
- Belum ada map view di aplikasi mobile.
- Belum ada face recognition asli.
- Belum ada dashboard mobile untuk parent, manager, atau admin.
- Push notification production membutuhkan konfigurasi Firebase manual.
