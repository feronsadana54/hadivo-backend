# Backend module breakdown

Semua modul ada di paket `com.hadivo.attendance.modules`. Tiap modul minimal berisi entity, repository, service, controller, dan DTO. Modul boleh memanggil service modul lain langsung (in-process), tapi tidak boleh menyentuh repository modul lain.

| Modul | Tanggung jawab utama |
| --- | --- |
| `auth` | User entity, JWT login/refresh/logout, refresh token rotation, super admin seeder |
| `tenant` | Tenant CRUD; pembuatan tenant otomatis bikin TENANT_ADMIN, settings, dan subscription FREE |
| `membership` | Daftar anggota per tenant, role enforcement (`MembershipGuard`) |
| `parentlink` | Relasi parent ↔ student per tenant |
| `subscription` | Plan + batas anggota; cek limit di `MembershipService` |
| `payment` | Package catalog, payment record, mock/Midtrans gateway, webhook, dan aktivasi subscription |
| `shift` | Shift template, assignment anggota, resolver jadwal harian, dan fallback attendance settings |
| `location` | Lokasi & radius geofence per tenant |
| `settings` | `tenant_attendance_settings` per tenant |
| `attendance` | Clock-in/out, attempts, history, event publishing |
| `geofence` | Validator jarak menggunakan Haversine |
| `face` | Interface `FaceVerifier` + impl demo |
| `notification` | Notification gateway: RabbitMQ publisher/consumer, template, mock/Resend/FCM gateways, token registration, in-app notifications, dan delivery log |
| `audit` | `AuditLogger` untuk menulis `audit_logs` |
| `reporting` | Laporan harian & bulanan dari `attendance_records` |

## Cross-cutting

- `common/exception` — `DomainException` + `GlobalExceptionHandler` map ke `ApiResponse`.
- `common/response` — `ApiResponse<T>`, `PageResponse<T>`.
- `common/security` — `JwtService`, `JwtAuthenticationFilter`, `@CurrentUser`.
- `common/util` — `GeoUtils` (Haversine), `TimeUtils` (timezone helper).
- `config` — Security, RabbitMQ, OpenAPI, properties.
