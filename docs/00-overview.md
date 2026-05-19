# Overview

Hadivo Attendance System adalah produk SaaS untuk merekam kehadiran berbasis lokasi (geofence) dan opsional verifikasi wajah. Targetnya dua segmen:

- **Sekolah** — kehadiran siswa, dengan notifikasi ke orang tua.
- **Perusahaan** — kehadiran karyawan, dengan opsi shift sederhana di fase berikutnya.

Satu deployment melayani banyak tenant. Setiap tenant punya pengaturan absensi sendiri (jam kerja, radius lokasi, ambang batas terlambat, kebutuhan verifikasi wajah).

## Komponen

- **Backend** — Spring Boot + Kotlin, satu modular monolith.
- **Postgres** — penyimpanan utama, transaksional.
- **RabbitMQ** — fan-out event absensi ke notifikasi.
- **Mobile (Fase 3)** — Flutter, dipakai user akhir.
- **Web (Fase 3)** — admin tenant (laporan, manajemen anggota).

## Pengguna

| Role | Tipikal user |
| --- | --- |
| `SUPER_ADMIN` | tim Hadivo, lintas tenant (Fase 2) |
| `TENANT_ADMIN` | admin sekolah / HR perusahaan |
| `MANAGER` | manajer (mode COMPANY) |
| `TEACHER` | guru (mode SCHOOL) |
| `EMPLOYEE` | karyawan |
| `STUDENT` | siswa |
| `PARENT` | orang tua |

## Skala awal

Fase 1 menargetkan ≤ 500 anggota per tenant, sesuai batas plan BUSINESS. ENTERPRISE unlimited dialokasikan untuk pelanggan khusus.

Tidak ada multi-region atau replikasi cross-region di Fase 1.
