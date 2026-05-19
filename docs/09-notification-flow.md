# Notification flow

## Tujuan utama: tidak boleh ada notifikasi palsu

Kalau transaksi DB rollback (mis. constraint UNIQUE jebol di detik terakhir), tidak boleh ada event yang sudah terkirim. Karena itu publish ke RabbitMQ harus dilakukan **setelah** commit.

## Pola

```
AttendanceService (Transactional)
   │
   │  publisher.publishEvent(ClockInOccurred(...))
   ▼
ApplicationEventMulticaster (Spring)
   │
   │  @TransactionalEventListener(phase = AFTER_COMMIT)
   ▼
AttendanceRabbitPublisher
   │
   │  rabbitTemplate.convertAndSend(exchange, routingKey, NotificationMessage)
   ▼
RabbitMQ exchange "attendance.events"
   │
   │  routing-key binding: attendance.#
   ▼
Queue "attendance.notifications"
   │
   │  @RabbitListener
   ▼
NotificationListener  →  notifications table
```

## Event domain

`ClockInOccurred`, `ClockOutOccurred`, `AttemptFailed`. Semua mengandung `tenantId`, `userId`, `occurredAt`, dan data spesifik.

## Routing keys

- `attendance.clockin`
- `attendance.clockout`
- `attendance.attempt.failed`

Queue `attendance.notifications` binding ke pattern `attendance.#` sehingga menerima ketiganya. Di fase berikutnya boleh tambah queue khusus per channel (mis. queue terpisah untuk FCM, email).

## Fan-out parent

Saat `NotificationListener` memproses pesan, kalau pelaku ber-role `STUDENT` maka query `parent_student_links` aktif dan menulis baris notifikasi tambahan per parent. Implementasi di `NotificationListener.handle`.

## Robustness

- `convertAndSend` dibungkus try/catch di `AttendanceRabbitPublisher`. Bila RabbitMQ down, kegagalan dilog tapi tidak meledak ke caller — record sudah tersimpan.
- Untuk Fase 2 tinggal tambahkan outbox table kalau butuh guarantee at-least-once yang lebih kuat saat broker outage.
