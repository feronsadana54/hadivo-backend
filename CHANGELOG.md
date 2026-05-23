# Changelog

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
