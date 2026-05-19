# System architecture

```
+-------------+        HTTPS         +---------------------+
|  Mobile /   | -------------------> |  Spring Boot API    |
|  Web (TBD)  |                      |  (modular monolith) |
+-------------+                      +----------+----------+
                                                |
                       +------------------------+------------------------+
                       |                        |                        |
                       v                        v                        v
                +-------------+         +----------------+        +---------------+
                | PostgreSQL  |         | RabbitMQ       |        | (future)      |
                | (Flyway)    |         | attendance.*   |        | FCM / Email   |
                +-------------+         +-------+--------+        +---------------+
                                                |
                                                v
                                        +---------------+
                                        | Notification  |
                                        | listener      |
                                        +---------------+
                                                |
                                                v
                                        +---------------+
                                        | notifications |
                                        | (Postgres)    |
                                        +---------------+
```

## Modular monolith

Backend dibagi per modul dalam satu artefak:

- `auth`, `tenant`, `membership`, `parentlink`, `subscription`
- `location`, `settings`
- `attendance`, `geofence`, `face`
- `notification`, `audit`, `reporting`

Lihat [`03-backend-module-breakdown.md`](03-backend-module-breakdown.md) untuk detail.

## Async event flow

`AttendanceService` mempublish Spring application event di dalam transaksi. `AttendanceRabbitPublisher` menangkap event itu dengan `@TransactionalEventListener(phase = AFTER_COMMIT)` dan baru mengirim ke RabbitMQ kalau transaksi DB berhasil commit. Pendekatan ini menjamin tidak ada notifikasi yang dipancarkan ketika transaksi DB rollback.

`NotificationListener` di sisi konsumer membaca pesan dari queue `attendance.notifications`, lalu menulis baris di tabel `notifications` untuk user pelaku dan (untuk STUDENT) parent terkait.
