# ADR 0002: Use RabbitMQ for async notification

- Status: Accepted
- Date: 2026-05-19

## Context

Setelah clock-in sukses, sistem perlu mengirim notifikasi ke user dan (untuk STUDENT) ke parent. Kita tidak ingin blocking HTTP response sampai fan-out selesai. Kita juga tidak ingin notifikasi dipancarkan saat transaksi DB rollback.

Pilihan yang dipertimbangkan:

1. Kirim sinkron di thread request. Sederhana tapi tidak elastis dan menahan response.
2. Spring application event in-process saja. Sederhana tapi tidak memberi opsi memindahkan consumer ke proses lain di kemudian hari.
3. RabbitMQ. Tambah komponen, tapi memberi fleksibilitas dan didukung Spring AMQP dengan baik.
4. Kafka. Berlebihan untuk volume saat ini.

## Decision

Gunakan RabbitMQ + Spring AMQP. `AttendanceService` publish Spring event di dalam transaksi. `@TransactionalEventListener(phase = AFTER_COMMIT)` menangkap event itu dan baru memanggil `RabbitTemplate.convertAndSend`. Consumer `NotificationListener` membaca dari queue `attendance.notifications` dan menulis ke tabel `notifications`.

## Consequences

- Tidak ada notifikasi yang lolos saat transaksi DB rollback.
- Failover sederhana — kalau broker down, send di-catch dan dilog; record sudah aman.
- Bisa diperluas ke channel lain (FCM, email) tanpa mengubah producer.
- Operasional: butuh menjalankan RabbitMQ tambahan. Sudah disiapkan di `docker-compose.yml`.
