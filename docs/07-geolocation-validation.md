# Geolocation validation

## Haversine

Menghitung jarak dua titik di permukaan bumi sebagai bola dengan radius 6.371 km. Cukup untuk skala absensi (kesalahan < 0.5% untuk jarak di bawah beberapa puluh km). Implementasi di `common/util/GeoUtils.kt`.

```
a = sin²(Δφ/2) + cos(φ1) · cos(φ2) · sin²(Δλ/2)
c = 2 · atan2(√a, √(1−a))
d = R · c
```

`distanceMeters(lat1, lon1, lat2, lon2)` mengembalikan jarak meter. `isWithinRadius(...)` membandingkan dengan radius lokasi.

## Validator

`GeofenceValidator.findMatching(lat, lon, locations)` melalui semua lokasi aktif dan mengembalikan yang pertama match. Order ditentukan oleh repository (saat ini tidak ada urutan eksplisit; kalau tenant punya overlap radius, lokasi yang dicocokkan adalah yang pertama dimasukkan).

## Validasi yang dilakukan

- Clock-in: harus ada lokasi yang match. Tidak ada match → reject.
- Clock-out: kalau `allow_clock_out_outside_radius = true`, lokasi yang tidak match boleh dilanjut tapi ditandai `clock_out_outside_radius = true`.

## Limitation

- Tidak ada perhitungan elevasi.
- Tidak ada deteksi spoofing GPS.
- Tidak ada tracking pergerakan saat di dalam radius. Hanya snapshot saat clock-in / clock-out.

## Test

`GeoUtilsTest` mengecek:

- Jarak titik ke dirinya sendiri = 0.
- Jakarta–Bandung sekitar 110–130 km.
- Offset 0.001° pada lat ≈ 111 m.
- `isWithinRadius` di pusat, di pinggir, dan di luar.
