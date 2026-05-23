# Changelog

## v0.7.0 - Notification Gateway Foundation

### Backend

- Added notification gateway domain abstractions for event type, channel, recipient, template, delivery status, delivery log, publisher, consumer, and gateway providers.
- Added `notification_delivery_logs` table for tenant-scoped notification delivery audit.
- Added RabbitMQ queue `hadivo.notification.events` for async notification processing.
- Added mock/log-only email and push gateways; no real FCM, Resend, or external provider integration.
- Added in-app delivery through the existing `notifications` table.
- Added templates for clock-in success, clock-out success, out-of-radius attendance, device mismatch, and failed attendance attempts.
- Integrated notification publishing with attendance success and failed attempt events after commit.
- Added read-only endpoint `GET /api/v1/tenants/{tenantId}/notification-deliveries`.
- Added audit logging for `NOTIFICATION_PUBLISHED`, `NOTIFICATION_SENT`, and `NOTIFICATION_FAILED`.
- Added integration tests for delivery logs, access guard, device mismatch notification, and best-effort publish failure handling.

### Web Dashboard

- Added Notifications menu item and `/notifications` page.
- Added delivery log table with event, channel, status, provider, recipient, and delivery timing.
- Added event, channel, and status filters.
- Added friendly empty, loading, and error states.
- Added Notification screenshot.

### Notes

- Notification gateway v0.7.0 is a foundation only.
- Email and push channels use mock/log-only providers.
- No Firebase Cloud Messaging, Resend, SMTP, SMS, API key, or production notification provider is active yet.
- No notification preference center, retry scheduler, or mobile push token registration yet.
- Attendance flow remains best effort for notification delivery, so notification failure does not fail the attendance mutation.

## v0.6.0 - Device Binding & Multi-Device Policy

### Backend

- Added `user_devices` table for per-tenant trusted attendance devices.
- Added device binding policy for clock-in and clock-out.
- Auto-registers the first attendance device for a user in a tenant.
- Rejects attendance from a different active trusted device with `DEVICE_MISMATCH`.
- Rejects missing or invalid device IDs with `INVALID_DEVICE`.
- Added tenant-scoped device list and reset endpoints for admins.
- Added audit logging for `DEVICE_REGISTERED`, `DEVICE_MISMATCH`, and `DEVICE_RESET`.
- Added integration tests for first registration, same-device attendance, mismatch rejection, reset behavior, and reset authorization.

### Web Dashboard

- Added member device status to the Members page.
- Added Reset Device action with confirmation for admin workflows.
- Added friendly device mismatch and invalid device error copy.

### Mobile App

- Replaced the static demo device ID with a privacy-friendly random device UUID stored in secure storage.
- Sends `deviceId`, `deviceName`, and `platform` with clock-in and clock-out requests.
- Added friendly device binding error messages.

### Notes

- Device binding is not a perfect anti-fraud control.
- Reinstalling the mobile app can generate a new device ID and require admin reset.
- Production can strengthen this later with platform attestation, liveness, MDM, or stricter device posture checks.
- No payment gateway, real face recognition, FCM/email gateway, or large Super Admin expansion.

## v0.5.0 - Super Admin Console + Cross-Tenant Analytics

### Backend

- Added read-only Super Admin endpoints for overview, tenant list, and tenant detail.
- Added platform overview analytics for tenant, member, attendance, failed attempt, and subscription counts.
- Added cross-tenant tenant list with search, type, status, subscription status, and page/size filters.
- Added tenant detail summary with member counts, attendance today, failed attempts today, current subscription, and recent failed attempts.
- Restricted `/api/v1/super-admin/**` data access to users with active `SUPER_ADMIN` membership.
- Added SUPER_ADMIN-only access guard and integration tests.
- Added audit log entries for Super Admin overview, tenant list, and tenant detail reads without sensitive metadata.
- Added integration tests for SUPER_ADMIN access, non-SUPER_ADMIN 403, tenant list/detail summaries, and sensitive field exclusions.
- No tenant edit/delete/impersonation yet.
- No payment gateway, real face recognition, production notification gateway, or device binding.

### Web Dashboard

- Added Super Admin navigation and pages for `/super-admin`, `/super-admin/tenants`, and `/super-admin/tenants/[tenantId]`.
- Added read-only overview cards, tenant type chart, subscription status chart, tenant table filters, tenant detail profile, and recent failed attempts.
- Added friendly 403 state for users without Super Admin access.
- Added Super Admin screenshots for overview, tenant list, and tenant detail.

### Documentation

- Documented Super Admin Console as v0.5.0 and clarified that it is read-only and SUPER_ADMIN-only.

## v0.4.0 - Security & Tenant Hardening

### Backend

- Added in-memory login lockout protection.
- Generic login error response to avoid account enumeration.
- Added password policy validation.
- Hardened refresh token tests for rotation and revoke behavior.
- Added basic security headers.
- Improved safe error responses for auth/access denied.
- Expanded audit log coverage for login, logout, refresh, tenant, member, parent link, location, settings, subscription, and CSV export actions.
- Added security hardening integration tests.
- No backend schema changes.
- No frontend UI changes.

### Documentation

- Added security baseline documentation.
- Documented tenant isolation, role-based access, audit logging, login lockout, password policy, refresh token hashing/rotation/revoke, and security headers.
- Documented that the login limiter is in-memory and production multi-instance deployments should use Redis or a centralized rate limiter.
- Kept known limitations explicit: real face recognition, payment gateway, and production notification gateway are not active yet.

## v0.3.0 - Address Search for Location Picker

### Web Dashboard

- Added address/place search to web location picker.
- Uses Nominatim OpenStreetMap search.
- Search is explicit via button/Enter, not live autocomplete.
- Admin can select a result to move map, marker, latitude, and longitude.
- Location name can be auto-filled when the name field is empty.
- Search validates minimum 3 characters.
- Request spam is reduced with loading guard and cancellation.
- No Google Maps, Mapbox, API key, or billing required.
- Web location screenshot refreshed.

### Notes

- Backend attendance validation, backend API, and database schema are unchanged.
- Routing/navigation is not included.
- Mobile app map view is not included yet.
- Public Nominatim is intended here for demo/portfolio and light requests; use an official/paid geocoding provider or self-hosted Nominatim for heavy production traffic.

## v0.2.0 - Map-Based Location Picker

### Web Dashboard

- Added Leaflet + OpenStreetMap map picker for attendance locations.
- Admin can click map to choose attendance point.
- Marker and radius circle update automatically.
- Latitude and longitude can still be edited manually.
- Radius preview is shown on map.
- Existing locations can be edited through the map form.
- OpenStreetMap attribution is preserved.
- No Google Maps API key or billing required.
- Web screenshots refreshed.

### Notes

- Backend attendance validation and location API contract are unchanged.
- Address search, autocomplete, and geocoding are not included yet.
- Mobile app map view is not included yet.
- For large production traffic, use an official/paid tile provider or self-hosted tiles that follow OpenStreetMap policy.

## v0.1.0 - Hadivo MVP

Initial MVP release for Hadivo Attendance System.

### Backend

- Kotlin Spring Boot MVP with modular monolith structure.
- JWT authentication with access token, refresh token, refresh token rotation, and logout.
- Multi-tenant foundation with tenant, membership, role, and parent-student link modules.
- Attendance clock-in and clock-out with geolocation radius validation.
- Attendance attempt logging for rejected actions such as out-of-radius, duplicate clock-in, missing clock-in, and face mismatch.
- Tenant attendance settings for work time, late threshold, face requirement flags, and clock-out radius policy.
- Subscription module with manual plan/status management.
- Reporting module for daily and monthly attendance summaries.
- CSV export for attendance reports.
- RabbitMQ notification flow for attendance events.
- Audit log support for attendance actions.
- Flyway migrations and local Docker Compose for PostgreSQL and RabbitMQ.

### Web Dashboard

- Next.js dashboard MVP for tenant admin workflows.
- Login page with JWT token storage.
- Dashboard summary cards and monthly attendance chart.
- Attendance report page with date/status filters and CSV export.
- Attendance attempts audit page.
- Members, settings, locations, and subscription pages.
- Responsive dashboard screenshots and documentation.
- User-friendly status badges, empty states, loading states, and error messages.

### Mobile App

- Flutter Mobile Attendance MVP for employee/student attendance.
- Login with secure token storage.
- Home screen for today's attendance status.
- Clock-in and clock-out actions with location payload.
- Demo location mode using the seeded tenant location for easier local demos.
- Attendance history for the last seven days.
- Profile screen and logout.
- Human-friendly mobile error messages for common backend error codes.

### CI and Documentation

- GitHub Actions Backend CI for Gradle tests with PostgreSQL and RabbitMQ services.
- GitHub Actions Web CI for lint and production build.
- GitHub Actions Mobile CI for Flutter dependency install, analyze, and tests.
- Root README with backend, web, mobile, screenshots, CI, and local setup notes.
- Mobile README with setup, demo accounts, demo location mode, validation, and screenshot capture guide.
- Postman collection for manual API QA.

### Known Limitations

- Face verification is still demo-only and does not perform real face recognition.
- No production notification gateway yet; FCM, email, and SMS are not implemented.
- Subscription/payment is manual and not integrated with a payment gateway.
- No mobile offline mode.
- No mobile map view.
- No parent/manager mobile dashboard.
- No PDF or Excel export for attendance reports.
- Device binding is not strict yet.
- Mobile screenshots are not included yet because no Android emulator capture is available.
