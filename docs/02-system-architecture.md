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
                | PostgreSQL  |         | RabbitMQ       |        | Optional      |
                | (Flyway)    |         | notification   |        | FCM / Resend  |
                +-------------+         +-------+--------+        +---------------+
                                                |
                                                v
                                        +---------------+
                                        | Notification  |
                                        | gateway       |
                                        +---------------+
                                                |
                                                v
                               +-------------------------+
                               | notifications +         |
                               | delivery logs           |
                               +-------------------------+
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

`NotificationConsumer` membaca queue `hadivo.notification.events`, resolve recipient dan template, lalu menulis `notification_delivery_logs`. Channel `IN_APP` menulis ke tabel `notifications`. Channel `EMAIL` dan `PUSH` memakai mock/log-only provider secara default, atau optional Resend/FCM jika konfigurasi real provider lengkap.
