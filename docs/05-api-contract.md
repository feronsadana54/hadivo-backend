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

## Body clock-in / clock-out

```json
{
  "latitude": -6.2,
  "longitude": 106.816666,
  "deviceId": "device-001",
  "faceImageBase64": "optional"
}
```

`tenantId` selalu dari path, tidak dari body.

## OpenAPI

Swagger UI di `/swagger-ui.html`. Skema bearer auth sudah didaftarkan di `OpenApiConfig`.
