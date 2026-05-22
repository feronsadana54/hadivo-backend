# Hadivo Mobile Attendance

Flutter mobile MVP untuk user attendance Hadivo. Scope app ini fokus pada login, status attendance hari ini, clock-in, clock-out, history, profile sederhana, dan logout.

UI mobile dibuat sederhana untuk employee/student agar mudah digunakan oleh user non-teknis. Teks tombol, status, empty state, dan error message dibuat ringkas dan manusiawi.

## Prerequisites

- Flutter SDK terpasang.
- Backend Hadivo berjalan lokal di port 8080.
- Demo tenant tersedia: `11111111-1111-1111-1111-111111111111`.

## Run

```
cd mobile
flutter pub get
flutter run
```

Default API base URL adalah:

```
http://10.0.2.2:8080
```

Nilai ini cocok untuk Android emulator yang mengakses backend di laptop host.

Untuk target web/local biasa, jalankan:

```
flutter run --dart-define=HADIVO_API_BASE_URL=http://localhost:8080
```

## Demo Login

Gunakan akun demo mobile:

- `employee@hadivo.local` / `ChangeMe123!`
- `student@hadivo.local` / `ChangeMe123!`

Kedua user dibuat oleh backend local/dev seed dan menjadi member demo tenant.

## Configuration

Konfigurasi utama ada di `lib/core/config/app_config.dart`.

Available dart defines:

- `HADIVO_API_BASE_URL`, default `http://10.0.2.2:8080`
- `HADIVO_TENANT_ID`, default `11111111-1111-1111-1111-111111111111`
- `HADIVO_USE_DEMO_LOCATION`, default `true`

Contoh menjalankan dengan GPS asli:

```
flutter run --dart-define=HADIVO_USE_DEMO_LOCATION=false
```

## Demo Location Mode

Default `HADIVO_USE_DEMO_LOCATION=true`. Saat aktif, clock-in dan clock-out mengirim koordinat seed location:

- latitude: `-6.2`
- longitude: `106.816666`

Mode ini memudahkan demo tanpa mengatur GPS emulator manual. Jika `HADIVO_USE_DEMO_LOCATION=false`, app meminta permission location dan mengambil posisi asli via `geolocator`.

## API Endpoints

Mobile app memakai endpoint:

- `POST /api/v1/auth/login`
- `GET /api/v1/tenants/{tenantId}/attendance/me/today`
- `POST /api/v1/tenants/{tenantId}/attendance/clock-in`
- `POST /api/v1/tenants/{tenantId}/attendance/clock-out`
- `GET /api/v1/tenants/{tenantId}/attendance/me?from=&to=`

## Validation

```
flutter pub get
flutter analyze
flutter test
```

## Screenshots

Belum ada screenshot mobile yang dicapture dari emulator. Capture dapat ditambahkan setelah app dijalankan di emulator/device.
