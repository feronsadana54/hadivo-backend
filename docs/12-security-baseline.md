# Security baseline

Dokumen ini merangkum baseline keamanan Hadivo setelah fase Security & Tenant Hardening v0.4.0 dan Super Admin Console v0.5.0.

## Tenant isolation

Endpoint tenant-scoped memakai `tenantId` dari path dan harus memvalidasi membership aktif user melalui `MembershipGuard` atau service yang setara. Data tenant tidak boleh diambil hanya berdasarkan input request tanpa cek akses tenant.

Endpoint yang dijaga termasuk tenant profile, memberships, parent links, subscriptions, locations, attendance settings, attendance, attendance attempts, dan reports. Endpoint lintas tenant `/api/v1/super-admin/**` hanya untuk role `SUPER_ADMIN` dan tidak bergantung pada membership tenant yang sedang dibuka.

## Role-based access

Hak akses tetap mengikuti role yang sudah ada:

- `TENANT_ADMIN` mengelola tenant sendiri.
- `MANAGER` dan `TEACHER` dapat melihat data tenant sesuai endpoint yang didukung.
- `EMPLOYEE` dan `STUDENT` fokus ke attendance diri sendiri.
- `PARENT` hanya untuk relasi anak melalui parent-student links.
- `SUPER_ADMIN` memiliki akses read-only ke Super Admin Console dan analytics lintas tenant.

## Login protection

Login memakai pesan generik untuk email salah, password salah, atau akun tidak aktif:

`Email atau password tidak sesuai.`

MVP login protection memakai in-memory failed login counter per email:

- 5 kali gagal dalam 15 menit akan mengunci login sementara.
- Lock berlangsung 15 menit.
- Login sukses mereset counter.
- Lockout memakai pesan: `Terlalu banyak percobaan login gagal. Coba lagi beberapa menit lagi.`

Limitasi: in-memory rate limit tidak cocok untuk production multi-instance karena counter tidak terbagi antar instance. Production sebaiknya memakai Redis atau rate limiter terpusat.

## Password policy

Password baru harus:

- minimal 8 karakter;
- memiliki setidaknya satu huruf;
- memiliki setidaknya satu angka;
- boleh memakai simbol, tetapi simbol tidak wajib.

Pesan validasi dibuat manusiawi dan tidak menampilkan detail internal.

## Refresh token

Refresh token disimpan sebagai SHA-256 hash, bukan token mentah. Refresh token memakai rotation:

- refresh token lama dicabut saat refresh berhasil;
- refresh token yang expired atau revoked ditolak;
- logout mencabut refresh token aktif;
- access token, refresh token, JWT, dan secret tidak boleh ditulis ke log atau audit metadata.

## Audit log

Audit log dipakai untuk aksi penting seperti login, logout, refresh token, tenant changes, membership changes, parent link changes, location changes, attendance settings update, attendance flow, subscription update, CSV export report, dan read access Super Admin Console.

Audit log menyimpan data secukupnya:

- `tenantId` jika ada;
- `actorUserId` jika ada;
- `action`;
- `resourceType`;
- `resourceId` jika ada;
- `metadataJson`;
- `createdAt`.

Metadata audit tidak boleh menyimpan password, access token, refresh token, JWT, secret, connection string, atau data rahasia lain. Untuk login failed, metadata cukup memakai email yang dimask dan reason umum.

## Security headers and errors

Backend menambahkan header dasar:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- `Cache-Control` default dari Spring Security untuk response sensitif

Content Security Policy belum dipaksa secara ketat agar Swagger UI tetap dapat dipakai. Error response tidak boleh membocorkan stack trace, class internal, SQL detail, token, password, secret, atau connection string.

## Future sensitive features

Real face recognition, payment gateway, device binding, dan notification gateway nyata belum aktif. Saat fitur tersebut ditambahkan, perlu review tambahan untuk privacy, consent, data retention, provider security, secret management, dan audit coverage.
