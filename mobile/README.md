# Hadivo Mobile Attendance

Hadivo Mobile adalah aplikasi Flutter MVP untuk user attendance seperti karyawan atau siswa. App ini dibuat sebagai pendamping backend Hadivo dan Web Dashboard, dengan alur sederhana agar user bisa login, melihat status absensi hari ini, clock-in, clock-out, melihat riwayat, dan logout.

UI mobile dibuat ringkas untuk user non-teknis. Teks tombol, status, empty state, dan pesan error dibuat mudah dipahami.

## Fitur MVP

- Login menggunakan akun demo employee/student.
- Home attendance today untuk melihat status absensi hari ini.
- Clock-in dengan lokasi.
- Clock-out dengan lokasi.
- Device binding menggunakan random device UUID yang disimpan di secure storage.
- Attendance history 7 hari terakhir.
- Profile sederhana.
- Logout.

## Tech Stack

- Flutter
- Dart
- Dio untuk HTTP client
- Riverpod untuk state management
- Go Router untuk routing
- Secure Storage untuk penyimpanan token
- Secure Storage untuk device ID absensi
- Geolocator untuk mengambil lokasi

## Prerequisites

- Flutter SDK terpasang.
- Backend Hadivo berjalan lokal di port 8080.
- Demo tenant tersedia: `11111111-1111-1111-1111-111111111111`.

## Cara Menjalankan

```
cd mobile
flutter pub get
flutter run
```

Default API base URL untuk Android emulator:

```
http://10.0.2.2:8080
```

Alamat `10.0.2.2` dipakai karena Android emulator mengakses backend yang berjalan di laptop host.

Untuk override base URL, gunakan dart define:

```
flutter run --dart-define=HADIVO_API_BASE_URL=http://localhost:8080
```

## Demo Login

Demo tenant ID:

```
11111111-1111-1111-1111-111111111111
```

Akun demo mobile:

- [employee@hadivo.local](mailto:employee@hadivo.local) / `ChangeMe123!`
- [student@hadivo.local](mailto:student@hadivo.local) / `ChangeMe123!`

Kedua user dibuat oleh backend local/dev seed dan menjadi member demo tenant.

## Demo Location Mode

Default app menggunakan:

```
HADIVO_USE_DEMO_LOCATION=true
```

Saat aktif, clock-in dan clock-out mengirim koordinat seed location:

- latitude: `-6.2`
- longitude: `106.816666`

Mode ini memudahkan demo tanpa mengatur GPS emulator manual.

Jika ingin menggunakan GPS asli, jalankan:

```
flutter run --dart-define=HADIVO_USE_DEMO_LOCATION=false
```

Saat GPS asli digunakan, app akan meminta izin lokasi. Jika izin ditolak, app menampilkan pesan yang mudah dipahami dan tidak crash.

## Device Binding

Mobile app membuat random device UUID saat pertama kali dipakai, lalu menyimpannya di secure storage. ID ini dikirim sebagai `deviceId` saat clock-in dan clock-out bersama `deviceName` dan `platform`.

App tidak memakai hardware identifier mentah. Jika app dihapus dan di-install ulang, device ID bisa berubah. Admin tenant dapat reset device user dari Web Dashboard agar perangkat baru bisa didaftarkan pada absensi berikutnya.

## API Endpoints

Mobile app memakai endpoint berikut:

- `POST /api/v1/auth/login`
- `GET /api/v1/tenants/{tenantId}/attendance/me/today`
- `POST /api/v1/tenants/{tenantId}/attendance/clock-in`
- `POST /api/v1/tenants/{tenantId}/attendance/clock-out`
- `GET /api/v1/tenants/{tenantId}/attendance/me?from=&to=`

## Screenshots

Folder screenshot mobile disiapkan di:

```
docs/images/mobile/
```

Screenshot asli belum ditambahkan karena environment saat dokumentasi ini dibuat tidak memiliki Android emulator yang tersedia. File yang disarankan untuk ditambahkan setelah capture manual:

- `docs/images/mobile/mobile-login.png`
- `docs/images/mobile/mobile-home.png`
- `docs/images/mobile/mobile-history.png`
- `docs/images/mobile/mobile-profile.png`
- `docs/images/mobile/mobile-clockin-success.png`

### Panduan Capture Screenshot Android

1. Jalankan infrastruktur dan backend lokal.
   ```
   docker compose -f docker/docker-compose.yml up -d
   cd backend
   .\gradlew.bat bootRun
   ```
2. Jalankan emulator Android.
3. Jalankan mobile app.
   ```
   cd mobile
   flutter pub get
   flutter run
   ```
4. Login dengan `employee@hadivo.local` / `ChangeMe123!`.
5. Capture layar login, home, history, profile, dan success clock-in.
6. Simpan file ke `docs/images/mobile/` dengan nama file yang tercantum di atas.

## Validation

```
flutter pub get
flutter analyze
flutter test
```

## Known Limitations

- Belum ada FCM atau push notification.
- Belum ada face recognition asli.
- Belum ada offline mode.
- Belum ada map view.
- Belum ada parent/manager mobile dashboard.
- Device binding bukan anti-fraud sempurna; production bisa memperkuat dengan attestation, liveness, MDM, atau posture checks.
