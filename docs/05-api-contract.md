# API contract

Base path: `/api/v1`. Semua endpoint kecuali auth memerlukan header `Authorization: Bearer <accessToken>`. Response sukses dibungkus:

```json
{ "data": { ... } }
```

Response gagal:

```json
{ "error": { "code": "OUT_OF_RADIUS", "message": "...", "details": null } }
```

`code` adalah salah satu nilai di `ErrorCode` (lihat `common/exception/ErrorCode.kt`).

## Endpoint

### Auth

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`

### Tenant & membership

- `POST /tenants`
- `GET /tenants/{tenantId}`
- `PATCH /tenants/{tenantId}`
- `POST /tenants/{tenantId}/memberships`
- `GET /tenants/{tenantId}/memberships`
- `DELETE /tenants/{tenantId}/memberships/{membershipId}`
- `GET /tenants/{tenantId}/members/{userId}/devices`
- `POST /tenants/{tenantId}/members/{userId}/devices/reset`

### Parent–student link

- `POST /tenants/{tenantId}/parent-links`
- `GET /tenants/{tenantId}/parent-links`
- `DELETE /tenants/{tenantId}/parent-links/{linkId}`

### Subscription

- `POST /tenants/{tenantId}/subscriptions`
- `GET /tenants/{tenantId}/subscriptions/current`

### Location & settings

- `POST /tenants/{tenantId}/locations`
- `GET /tenants/{tenantId}/locations`
- `PATCH /tenants/{tenantId}/locations/{locationId}`
- `GET /tenants/{tenantId}/attendance-settings`
- `PATCH /tenants/{tenantId}/attendance-settings`

### Attendance

- `POST /tenants/{tenantId}/attendance/clock-in`
- `POST /tenants/{tenantId}/attendance/clock-out`
- `GET /tenants/{tenantId}/attendance/me/today`
- `GET /tenants/{tenantId}/attendance/me?from=&to=`
- `GET /tenants/{tenantId}/attendance-attempts?userId=&from=&to=`

### Reporting

- `GET /tenants/{tenantId}/reports/attendance/daily?date=`
- `GET /tenants/{tenantId}/reports/attendance/monthly?month=`
- `GET /tenants/{tenantId}/reports/attendance/export.csv?from=YYYY-MM-DD&to=YYYY-MM-DD`

Endpoint export attendance mengembalikan `text/csv` dengan attachment filename
`hadivo-attendance-report-{from}-to-{to}.csv`. Range MVP maksimal 31 hari.

### Super Admin

Endpoint berikut hanya boleh diakses user dengan role `SUPER_ADMIN`. Fitur ini read-only untuk platform owner dan tidak menyediakan edit/delete tenant atau impersonation.

- `GET /super-admin/overview`
- `GET /super-admin/tenants?type=COMPANY|SCHOOL&status=ACTIVE|INACTIVE&subscriptionStatus=ACTIVE|EXPIRED|CANCELLED&search=&page=&size=`
- `GET /super-admin/tenants/{tenantId}`

`/super-admin/overview` mengembalikan ringkasan lintas tenant:

```json
{
  "data": {
    "totalTenants": 1,
    "activeTenants": 1,
    "companyTenants": 0,
    "schoolTenants": 1,
    "totalMembers": 3,
    "attendanceToday": 2,
    "failedAttemptsToday": 1,
    "activeSubscriptions": 1,
    "expiredSubscriptions": 0,
    "generatedAt": "2026-05-23T10:00:00Z"
  }
}
```

`/super-admin/tenants` memakai format pagination `PageResponse`:

```json
{
  "data": {
    "items": [
      {
        "tenantId": "11111111-1111-1111-1111-111111111111",
        "tenantName": "Hadivo Demo School",
        "tenantType": "SCHOOL",
        "active": true,
        "status": "ACTIVE",
        "memberCount": 3,
        "attendanceToday": 2,
        "failedAttemptsToday": 1,
        "subscriptionPlan": "FREE",
        "subscriptionStatus": "ACTIVE",
        "createdAt": "2026-05-23T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

Response Super Admin tidak mengekspos `passwordHash`, access token, refresh token, JWT secret, atau secret lain.

Limitasi v0.5.0: Super Admin masih read-only, belum ada endpoint edit/delete tenant, belum ada impersonation, belum ada billing/payment detail, dan analytics masih basic.

### Device Binding

Endpoint device member hanya boleh diakses admin tenant (`TENANT_ADMIN`) atau `SUPER_ADMIN` yang memiliki akses tenant sesuai guard yang berlaku.

- `GET /tenants/{tenantId}/members/{userId}/devices`
- `POST /tenants/{tenantId}/members/{userId}/devices/reset`

Response device item:

```json
{
  "deviceId": "7e8f3c8b-6219-44be-97f1-2c62a604b217",
  "deviceName": "Hadivo Mobile Android",
  "platform": "Android",
  "trusted": true,
  "active": true,
  "firstSeenAt": "2026-05-23T10:00:00Z",
  "lastSeenAt": "2026-05-23T10:00:00Z"
}
```

Policy v0.6.0:

- Clock-in/clock-out pertama dari user pada tenant akan otomatis mendaftarkan `deviceId` sebagai trusted device.
- Clock-in/clock-out berikutnya dari `deviceId` yang sama diizinkan dan memperbarui `lastSeenAt`.
- Clock-in/clock-out dari device berbeda ditolak dengan `DEVICE_MISMATCH` dan dicatat di `attendance_attempts`.
- `deviceId` kosong atau tidak valid ditolak dengan `INVALID_DEVICE`.
- Reset device membuat active trusted device menjadi inactive. Setelah reset, clock-in/clock-out berikutnya dari device baru akan auto-register lagi.
- Tidak ada endpoint write/edit/delete tenant dari fitur ini.

## Body clock-in / clock-out

```json
{
  "latitude": -6.2,
  "longitude": 106.816666,
  "deviceId": "device-001",
  "deviceName": "Hadivo Mobile Android",
  "platform": "Android",
  "faceImageBase64": "optional"
}
```

`tenantId` selalu dari path, tidak dari body.

## OpenAPI

Swagger UI di `/swagger-ui.html`. Skema bearer auth sudah didaftarkan di `OpenApiConfig`.
