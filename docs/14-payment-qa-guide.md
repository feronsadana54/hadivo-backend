# Payment QA guide

Panduan ini dipakai untuk QA manual payment v1.0.1. Fokusnya adalah memastikan payment foundation v1.0.0 mudah diuji secara lokal tanpa secret production dan tanpa endpoint debug production.

## Prasyarat local dev

- JDK 21.
- Docker Desktop atau Docker Engine dengan Docker Compose.
- Node.js 20 untuk web dashboard.
- Postman jika ingin menjalankan request manual.
- Secret Midtrans sandbox hanya disimpan di environment lokal, bukan di repo.

## Jalankan PostgreSQL dan RabbitMQ

Dari root repo:

```powershell
docker compose -f docker/docker-compose.yml up -d
```

Service lokal:

- PostgreSQL: `localhost:5432`, database/user/password default `hadivo`.
- RabbitMQ: `localhost:5672`.
- RabbitMQ management UI: `http://localhost:15672`, user/password `hadivo` / `hadivo`.

## Jalankan backend

Dari root repo:

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend tersedia di `http://localhost:8080`. Swagger UI tersedia di `http://localhost:8080/swagger-ui.html`.

Default payment provider adalah `mock`, sehingga backend dapat berjalan tanpa Midtrans key.

## Jalankan web

Di terminal terpisah:

```powershell
cd web
npm install
npm run dev
```

Web tersedia di `http://localhost:3000`.

## Login sebagai TENANT_ADMIN

Cara paling bersih untuk QA role `TENANT_ADMIN`:

1. Register user baru lewat Postman `Auth / Register`.
2. Login user tersebut lewat `Auth / Login`.
3. Buat tenant lewat `Tenants / Create Tenant`.
4. Simpan tenant id dari response ke variable Postman `tenantId`.

Endpoint create tenant otomatis membuat membership `TENANT_ADMIN` untuk user pembuat tenant.

Cara cepat untuk demo tenant:

- Login `superadmin@hadivo.local` / `ChangeMe123!`.
- Tenant demo: `11111111-1111-1111-1111-111111111111`.
- Role `SUPER_ADMIN` juga diizinkan untuk endpoint payment tenant-scoped di v1.0.1.

## Lihat package subscription

Postman:

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/subscription-packages
Authorization: Bearer {{accessToken}}
```

Web:

1. Login ke dashboard.
2. Buka `http://localhost:3000/subscription`.
3. Pastikan selector paket menampilkan nama paket, billing period, dan nominal.

## Buat mock payment dari web

1. Pastikan backend berjalan dengan provider default `mock`.
2. Buka halaman `/subscription`.
3. Pilih paket.
4. Klik `Buat Pembayaran`.
5. Pastikan tombol `Buka Halaman Pembayaran` muncul.
6. Pastikan riwayat payment menampilkan status `Menunggu`.

Mock payment tidak mengaktifkan subscription dari frontend. Aktivasi tetap hanya boleh terjadi setelah backend menerima webhook valid.

## Lihat payment history

Postman:

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/subscription-payments
Authorization: Bearer {{accessToken}}
```

Detail payment:

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/subscription-payments/{{paymentId}}
Authorization: Bearer {{accessToken}}
```

Pastikan response tidak memuat `rawWebhookJson`, `signature_key`, `server_key`, client key, token, atau secret lain.

## Buka payment URL

Untuk provider `mock`, `paymentUrl` berbentuk:

```text
http://localhost:8080/mock-payments/{providerOrderId}
```

URL ini adalah dummy URL untuk QA pembuatan payment request. Jika dibuka dan menampilkan 404 di backend lokal, itu masih wajar karena v1.0.1 tidak menambahkan halaman pembayaran mock dan tidak membuat payment sukses dari frontend.

Untuk provider Midtrans Snap sandbox, `paymentUrl` mengarah ke halaman Snap sandbox dari Midtrans.

## Simulasikan webhook paid, failed, dan expired

Tidak ada endpoint test-only untuk membuat payment sukses. Simulasi status dilakukan dengan memanggil webhook Midtrans memakai signature valid.

Untuk local/dev, jalankan backend dengan server key dummy yang sama dengan variable Postman:

```powershell
$env:MIDTRANS_SERVER_KEY="local-midtrans-test-key"
cd backend
.\gradlew.bat bootRun
```

Di Postman set variable:

- `providerOrderId`: dari response create payment.
- `grossAmount`: dari response create payment, contoh `99000.00`.
- `midtransServerKey`: `local-midtrans-test-key`.

Signature Midtrans dihitung dari:

```text
SHA512(order_id + status_code + gross_amount + server_key)
```

Collection Postman v1.0.1 sudah menambahkan pre-request script untuk menghitung `signatureKey` dari variable tersebut.

Status sample:

- Paid: `transaction_status=settlement`, `status_code=200`.
- Expired: `transaction_status=expire`, `status_code=407`.
- Failed: `transaction_status=failure`, `status_code=202`.

Endpoint webhook:

```http
POST {{baseUrl}}/api/v1/payments/webhooks/midtrans
Content-Type: application/json
```

Webhook tidak membutuhkan JWT, tetapi wajib lolos signature dan amount check.

## Cek subscription aktif setelah paid

Setelah webhook paid sukses:

```http
GET {{baseUrl}}/api/v1/tenants/{{tenantId}}/subscriptions/current
Authorization: Bearer {{accessToken}}
```

Expected:

- `status` tetap `ACTIVE`.
- `plan` berubah sesuai package yang dibayar, misalnya `PRO`.
- `expiresAt` terisi sesuai duration package.
- Payment detail berubah ke `PAID` dan `paidAt` terisi.

## Cek audit log

Cek lewat PostgreSQL lokal:

```sql
select action, resource_type, resource_id, metadata_json, created_at
from audit_logs
where action in (
  'PAYMENT_CREATED',
  'PAYMENT_WEBHOOK_RECEIVED',
  'PAYMENT_STATUS_UPDATED',
  'SUBSCRIPTION_ACTIVATED',
  'PAYMENT_WEBHOOK_IGNORED'
)
order by created_at desc;
```

Expected action:

- `PAYMENT_CREATED` saat payment request dibuat.
- `PAYMENT_WEBHOOK_RECEIVED` saat webhook order id ditemukan.
- `PAYMENT_STATUS_UPDATED` saat status berubah.
- `SUBSCRIPTION_ACTIVATED` saat webhook paid mengaktifkan subscription.
- `PAYMENT_WEBHOOK_IGNORED` untuk duplicate webhook, invalid signature, amount mismatch, late downgrade, missing order id, atau unknown order id.

Audit metadata tidak boleh menyimpan password, JWT, refresh token, Midtrans server key, client key, atau signature mentah.

## Setup Midtrans sandbox manual

Gunakan hanya key sandbox. Set environment lokal:

```powershell
$env:HADIVO_PAYMENT_PROVIDER="midtrans"
$env:HADIVO_PAYMENT_MIDTRANS_ENABLED="true"
$env:HADIVO_PAYMENT_MIDTRANS_ENVIRONMENT="sandbox"
$env:MIDTRANS_SERVER_KEY="..."
$env:MIDTRANS_CLIENT_KEY="..."
$env:MIDTRANS_SNAP_BASE_URL="https://app.sandbox.midtrans.com"
$env:MIDTRANS_API_BASE_URL="https://api.sandbox.midtrans.com"
```

Jalankan backend setelah env diset. Jika provider diset `midtrans` tetapi server key kosong atau provider tidak enabled, backend tetap fallback ke mock provider.

Di dashboard Midtrans sandbox, arahkan Payment Notification URL ke:

```text
https://<public-tunnel>/api/v1/payments/webhooks/midtrans
```

Untuk expose local backend gunakan tunnel lokal pilihanmu. Jangan commit config tunnel atau secret.

## Env payment yang relevan

- `HADIVO_PAYMENT_PROVIDER`: `mock` atau `midtrans`.
- `HADIVO_PAYMENT_MIDTRANS_ENABLED`: `true` hanya untuk Midtrans.
- `HADIVO_PAYMENT_MIDTRANS_ENVIRONMENT`: `sandbox` untuk QA.
- `MIDTRANS_SERVER_KEY`: server key sandbox/local dummy.
- `MIDTRANS_CLIENT_KEY`: client key sandbox.
- `MIDTRANS_SNAP_BASE_URL`: default `https://app.sandbox.midtrans.com`.
- `MIDTRANS_API_BASE_URL`: default `https://api.sandbox.midtrans.com`.

## Secret yang tidak boleh di-commit

- `MIDTRANS_SERVER_KEY` asli.
- `MIDTRANS_CLIENT_KEY` asli.
- Kunci privat atau service account JSON.
- GitHub personal access token.
- File `.env` asli.
- JWT, refresh token, FCM token, atau credential provider lain.

## Error response yang perlu dicek

- Package tidak ditemukan: `NOT_FOUND`, message `Paket subscription tidak ditemukan`.
- Role tidak punya akses: `FORBIDDEN`, message role atau tenant membership yang jelas.
- Tenant tidak valid untuk user: `FORBIDDEN` atau parameter invalid jika UUID salah format.
- Provider error: `UNPROCESSABLE`, message `Payment provider belum tersedia. Coba lagi beberapa saat.`
- Webhook invalid signature: `VALIDATION_FAILED`, message `Signature webhook payment tidak valid`.
- Amount mismatch: `VALIDATION_FAILED`, message `Nominal webhook payment tidak sesuai`.
- Order id tidak ditemukan: `VALIDATION_FAILED`, message `Order ID payment tidak ditemukan`.

## Common troubleshooting

- `401 UNAUTHORIZED`: login ulang dan pastikan `accessToken` tersimpan di Postman.
- `403 FORBIDDEN`: user belum menjadi member tenant atau role bukan `TENANT_ADMIN` / `SUPER_ADMIN`.
- `404 Paket subscription tidak ditemukan`: refresh package list dan pakai id package aktif dari response backend.
- Webhook invalid signature: pastikan `grossAmount`, `status_code`, `providerOrderId`, dan `midtransServerKey` sama persis dengan backend.
- Amount mismatch: pakai nominal dari response create payment; jangan isi amount manual.
- Payment tetap `PENDING`: webhook belum terkirim, signature salah, amount mismatch, atau order id salah.
- Tombol payment URL mock membuka 404: normal untuk provider mock v1.0.1.
- Midtrans Snap tidak aktif: cek `HADIVO_PAYMENT_PROVIDER=midtrans`, `HADIVO_PAYMENT_MIDTRANS_ENABLED=true`, dan `MIDTRANS_SERVER_KEY` tidak kosong.
