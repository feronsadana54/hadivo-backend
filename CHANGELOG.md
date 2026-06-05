# Changelog

## v1.6.0 - Face Recognition Enrollment Foundation

### Backend

- Added `user_face_profiles` table per (tenant, user) with `enrollment_status` (`PENDING` / `ACTIVE` / `RESET`), `consent_given` + `consent_given_at`, `image_reference` (relatif, bukan absolute path), `embedding_reference` (disiapkan, selalu null di v1.6.0), `enrolled_at`, dan `reset_at`. Migration `V12__add_face_enrollment_foundation.sql` dengan UNIQUE `(tenant_id, user_id)`.
- Added `FaceEnrollmentService` dengan operasi `getOrEmpty` / `enroll` / `reset`. Enroll wajib `consentGiven=true`, kalau tidak ditolak `VALIDATION_FAILED`. Re-enroll mengganti referensi DB dan menghapus file enrollment lama. Reset membersihkan `image_reference` + `embedding_reference`, set status `RESET`, dan best-effort menghapus file lokal tanpa mengangkat error (log warning tanpa path lengkap).
- Added `FaceImageStorage` yang menyimpan foto ke disk lokal di bawah `hadivo.face.storage-dir` (default `backend/storage/face`, env `HADIVO_FACE_STORAGE_DIR`). Validasi: base64 size cap (~5 MB), magic bytes JPEG (`FF D8 FF`) atau PNG (`89 50 4E 47`). Format lain ditolak. Path traversal dicegah saat delete.
- Added `FaceEnrollmentController` dengan endpoint `GET` / `POST .../enroll` / `POST .../reset` di scope `/api/v1/tenants/{tenantId}/members/{userId}/face-profile`. Reset admin-only; enroll dan get boleh diri sendiri atau admin. Cross-user tanpa role admin ditolak.
- Response `FaceProfileView` sengaja hanya mengembalikan `profileId`, `enrollmentStatus`, `consentGiven`, `imageStored`, `enrolledAt`, `resetAt`, `updatedAt`, dan `message`. `imageReference`, `embeddingReference`, absolute path, dan base64 tidak diekspos.
- Audit actions `FACE_PROFILE_ENROLLED` dan `FACE_PROFILE_RESET`. Metadata hanya `userId`, `enrollmentStatus`, `consentGiven`, `imageStored`, dan `profileId`. Tidak menyimpan `imageBase64`, `imageReference`, `embeddingReference`, atau path.
- Added `FaceEnrollmentIntegrationTest` (10 kasus): enroll happy path + file fisik tersimpan, tolak tanpa consent, tolak format selain JPEG/PNG, response tidak mengekspos `imageReference` / path / sample base64, admin reset clears references + delete file lokal, employee dilarang reset, re-enroll after reset menghasilkan referensi baru, re-enroll menghapus file sebelumnya, audit metadata bebas dari base64 / path, cross-user enroll forbidden, dan get untuk user belum-enroll mengembalikan `PENDING`.

### Web Dashboard

- Halaman `/members` menambahkan kolom `Wajah` per anggota dengan badge status (`Terdaftar` / `Direset` / `Belum enroll`), badge `Foto tersimpan` saat `imageStored=true`, tombol `Reset Wajah` (admin-only) saat foto tersimpan, dan catatan jelas: *Status ACTIVE berarti foto dan persetujuan tercatat. Pencocokan wajah belum aktif.*
- Added types `FaceEnrollmentStatus`, `FaceProfile`, `EnrollFaceRequest`. Added endpoints `memberFaceProfile`, `memberFaceProfileEnroll`, `memberFaceProfileReset`. Added services `getMemberFaceProfile` / `enrollMemberFace` / `resetMemberFaceProfile` dan hooks `useMemberFaceProfile` / `useEnrollMemberFace` / `useResetMemberFaceProfile`.

### Mobile

- Added fitur `face_profile` dengan `FaceProfile` domain, `FaceProfileRepository`, dan layar `FaceEnrollmentScreen` (`/profile/face`) untuk memilih foto via `image_picker` (kamera atau galeri), centang consent, dan submit. Card baru di `ProfileScreen` menampilkan status terkini dan link ke layar enrollment.
- Added `image_picker: ^1.1.2` di `pubspec.yaml`. Added `NSCameraUsageDescription` dan `NSPhotoLibraryUsageDescription` di iOS `Info.plist` dengan wording yang menjelaskan bahwa foto disimpan di server dan pencocokan wajah belum dilakukan. Tidak ada perubahan Android manifest.
- Added unit test `face_profile_test.dart` untuk `FaceProfile.fromJson` (ACTIVE / RESET / unknown → PENDING). Tidak ada test yang memerlukan kamera real.

### Privacy & storage

- Added `backend/storage/` ke `.gitignore` agar sample image, base64 sample besar, dan biometric data tidak pernah masuk ke repo.
- Storage strategy: local disk di bawah `hadivo.face.storage-dir`, layout `<root>/<tenantId>/<userId>-<timestampUtc>.<jpg|png>`, dengan path relatif tersimpan di DB. Reset best-effort menghapus file lokal; re-enroll menggantikan referensi dan menghapus file lama.

### Docs

- Added `docs/25-face-enrollment.md` covering scope, schema, status semantics, endpoints, response shape, validasi format (bukan validasi wajah), local storage layout, reset cleanup behavior, audit actions, web/mobile changes, dan limitations.
- Updated `docs/08-face-verification-flow.md` dengan pointer ke `docs/25-face-enrollment.md` dan penegasan bahwa face detection / matching / liveness / anti-spoofing belum diimplementasi.
- Updated `README.md` highlights dan link ke dokumen baru.

### Notes

- Clock-in / clock-out flow tidak diubah dan tidak diblokir oleh status enrollment.
- Payment, leave, holiday, dan attendance correction flow tidak diubah.
- v1.6.0 belum mengimplementasi face detection, face matching, liveness detection, anti-spoofing, embedding engine, maupun endpoint admin approve/reject enrollment.

## v1.5.1 - Holiday / Workday QA Stabilization

### Docs

- Added holiday/workday QA guide at `docs/24-holiday-workday-qa-guide.md` covering local setup, demo accounts, Postman variables, default and updated workday settings flow, holiday create/list/disable flow, ANNUAL_LEAVE Fri–Mon deduct = 2, Sat–Sun-only rejection, active holiday inside range (Mon–Wed = 2 instead of 3), inactive holiday no-impact, DB verification, cross-year limitation, role/cross-tenant access, web `/calendar` QA, mobile no-change note, troubleshooting, and v1.5.1 limitations.
- Linked the new guide from `README.md` and `docs/23-holiday-workday-calendar.md`.

### Postman

- Added `Holiday Workday QA Flow` covering get/update workday settings, create/list/disable holiday, ANNUAL_LEAVE Fri–Mon happy path, weekend-only rejection at approve, active-holiday-in-range deduction, and balance re-read after each step.
- Added collection variables `holidayId`, `holidayDate`, `holidayRangeFrom`, `holidayRangeTo`. Reused existing `annualLeaveRequestId`. Dates picked to avoid clashing with holidays seeded by earlier flows or tests (`2026-09-04..09-07` Fri–Mon, `2026-09-12..09-13` weekend, `2026-09-14..09-16` Mon–Wed with `2026-09-15` Tue as the active holiday).

### Backend

- Added 5 small integration tests in `WorkdayHolidayIntegrationTest` to close validation gaps in v1.5.0:
  - `list rejects range more than one year` — exercises the 366-day cap on `GET /holidays`.
  - `holiday create rejects missing holidayDate` — guards `CreateHolidayRequest.holidayDate`.
  - `holiday create rejects blank name` — guards `name` trim/empty check.
  - `holiday create rejects invalid type` — guards `HolidayType.parse` fallback.
  - `employee can list holidays without mutation access` — positive read check for non-admin role on both `/holidays` and `/workday-settings`.

### Notes

- No endpoint, migration, schema, or service behavior changes. No payment / attendance correction apply / face recognition changes. Web UI and mobile UI are not modified.

## v1.5.0 - Holiday / Workday Calendar Foundation

### Backend

- Added `tenant_workday_settings` table (per-tenant UNIQUE) with seven `*_workday` booleans (Mon–Fri default true, Sat–Sun default false) and `active` flag. Migration `V11__add_holiday_workday_calendar.sql`.
- Added `tenant_holidays` table with `holiday_date`, `name`, `type` (`CUSTOM`/`NATIONAL`/`COMPANY`/`SCHOOL`), and `active`. UNIQUE `(tenant_id, holiday_date, name)` and lookup index `(tenant_id, active, holiday_date)` for workday calc.
- Added `WorkdaySettingsService` with `GET` / `PUT /api/v1/tenants/{tenantId}/workday-settings`. Default created lazily on first GET. Update rejects all-days-false with `VALIDATION_FAILED` "Minimal satu hari dalam seminggu harus menjadi hari kerja".
- Added `HolidayService` with `GET` (range filter, max 366 days), `POST`, and `PATCH /api/v1/tenants/{tenantId}/holidays`. No DELETE endpoint; soft-disable via `PATCH active=false`. Duplicate `(date, name)` returns `409 CONFLICT`.
- Added `WorkdayCalendarService.countWorkdays(tenantId, start, end)` returning the number of workdays in the inclusive range, excluding days marked non-workday in settings and active holidays. Range capped at 31 days.
- Wired `WorkdayCalendarService` into `LeaveBalanceService.deductForApproval`. `ANNUAL_LEAVE` now deducts the **workday count**, not calendar count. Requests with 0 workdays (e.g. Sat–Sun under default settings) are rejected with `VALIDATION_FAILED` "Pengajuan cuti tidak memiliki hari kerja yang dapat dipotong." and stay PENDING.
- Added `leaveDays` field to `LeaveRequestView` (computed via WorkdayCalendarService for ANNUAL_LEAVE only, null otherwise). Cross-year requests yield `null` to avoid throwing during view rendering.
- Added audit actions `WORKDAY_SETTINGS_UPDATED`, `HOLIDAY_CREATED`, `HOLIDAY_UPDATED`.
- Added `WorkdayHolidayIntegrationTest` (14 cases) covering policy/holiday CRUD with role and cross-tenant guards, list-range guards, duplicate conflict, and workday calculation (default weekend exclusion, Saturday opt-in, active/inactive holiday). Extended `LeaveBalanceIntegrationTest` with three workday-aware cases: Fri–Mon deducts 2 not 4, only-weekend approval rejected with PENDING preserved, holiday inside range reduces deduction.

### Web Dashboard

- Added `/calendar` page (Kalender Kerja) with three cards: workday checkboxes (Mon–Sun, Save), add holiday form (date + name + type), and holiday list with year filter and Activate/Deactivate toggle.
- Added sidebar entry "Kalender Kerja" (`CalendarDays` icon) between "Saldo Cuti" and "Notifikasi".
- `/leave-requests` now shows "X hari kerja" for `ANNUAL_LEAVE` rows when backend returns `leaveDays`, falling back to calendar count otherwise.
- Added types `WorkdaySettings`, `UpdateWorkdaySettingsRequest`, `Holiday`, `HolidayType`, `CreateHolidayRequest`, `UpdateHolidayRequest`, and `leaveDays?: number | null` on `LeaveRequest`. Added endpoints, services, and hooks `useWorkdaySettings`, `useUpdateWorkdaySettings`, `useHolidays`, `useCreateHoliday`, `useUpdateHoliday`.

### Mobile

- No mobile changes. Backend response remains additive; mobile leave-request parser ignores the new `leaveDays` field.

### Docs

- Added `docs/23-holiday-workday-calendar.md` covering schema, endpoints, workday calculation, ANNUAL_LEAVE deduction impact with four worked examples (Fri–Mon, Saturday-on, holiday-in-range, weekend-only), audit actions, web UX, and limitations.
- Updated `README.md` highlights and added pointer to the new doc.
- Updated `docs/04-database-design.md`, `docs/05-api-contract.md`, `docs/16-leave-permission.md`, `docs/21-leave-balance.md`, and `docs/22-leave-balance-qa-guide.md` with v1.5.0 cross-references.

### Notes

- Workday calc applies only to `ANNUAL_LEAVE` deduction. Other leave types and attendance / shift / payroll flows remain on calendar logic.
- Holiday auto sync from a national calendar provider, regional/branch overrides, half-day leave, calendar file import, Google Calendar integration, and DELETE endpoint for holidays are out of scope.

## v1.4.1 - Leave Balance QA Stabilization

### Docs

- Added leave balance QA guide at `docs/22-leave-balance-qa-guide.md` covering local setup, demo accounts, Postman variables, happy path, DB verification, idempotency (force-PENDING re-approve), insufficient balance via small adjust + 2-3 day request, SICK/PERMISSION/BUSINESS_TRIP/ATTENDANCE_CORRECTION non-deduct, cross-year rejection, adjustment guards, role/cross-tenant access, web `/leave-balances` and mobile profile card QA, troubleshooting, and v1.4.1 limitations.
- Linked the new guide from `README.md` and `docs/21-leave-balance.md`.

### Postman

- Added `Leave Balance QA Flow` collection covering get/update policy, list/get/adjust balance, ANNUAL_LEAVE create/approve happy path, insufficient-balance flow (small adjust → 2-3 day request → expect 400), SICK and ATTENDANCE_CORRECTION non-deduct verification, and balance re-read after each step.
- Added collection variables `memberUserId`, `balanceYear`, `adjustmentDays`.

### Backend

- Added 3 integration tests to harden v1.4.0 against regression:
  - `employee cannot adjust balance` — closes role-guard gap (admin-only adjust).
  - `deductForApproval is idempotent at service level` — invokes the service twice with the same approved request and verifies only one DEDUCT ledger.
  - `balance ledger history captures initial adjust and deduct change types` — asserts the set `{INITIAL, ADJUST, DEDUCT}` after a full lifecycle, without depending on `created_at` ordering.

### Notes

- No endpoint, migration, schema, or service behavior changes. No payment / attendance correction apply engine changes. Web UI and mobile UI are not modified.

## v1.4.0 - Leave Balance / Quota Foundation

### Backend

- Added `leave_policies` table (per-tenant unique) with `annual_leave_quota_days` default 12 and per-type `*_requires_balance` flags (default false). Migration `V10__add_leave_balance_foundation.sql`.
- Added `leave_balances` table with `(tenant_id, user_id, year)` UNIQUE and per-year quota / used / adjusted / remaining columns.
- Added `leave_balance_ledgers` table tracking every change (`INITIAL`, `DEDUCT`, `ADJUST`, `RESTORE`) with before/after snapshot, optional `leave_request_id`, optional `note`, and partial UNIQUE index `(leave_request_id) WHERE change_type='DEDUCT'` for idempotency.
- Added `LeavePolicyService` and endpoints `GET` / `PUT /api/v1/tenants/{tenantId}/leave-policy`. Default policy is created lazily on first GET. PUT allowed only for `TENANT_ADMIN` and `SUPER_ADMIN`. Quota validated `0..365`.
- Added `LeaveBalanceService` and endpoints `GET /leave-balances?year=YYYY`, `GET /members/{userId}/leave-balance?year=YYYY`, and `POST /members/{userId}/leave-balance/adjust`. Employee can read own balance; admin can read all and adjust. Adjust requires `note`, rejects zero `days`, rejects `year` outside `[currentYear-1, currentYear+1]`, and rejects results that would make `remaining_days` negative.
- Wired `LeaveBalanceService.deductForApproval` into `LeaveRequestService.review()` for `ANNUAL_LEAVE` approvals. Insufficient balance or cross-year requests throw `VALIDATION_FAILED`, rolling back the approve so the request stays `PENDING`. Sick / permission / business trip / attendance correction do not deduct in v1.4.0.
- Cross-year `ANNUAL_LEAVE` requests are rejected with "Pengajuan cuti lintas tahun belum didukung."
- Added audit actions `LEAVE_POLICY_UPDATED`, `LEAVE_BALANCE_INITIALIZED`, `LEAVE_BALANCE_ADJUSTED`, `LEAVE_BALANCE_DEDUCTED`.
- Added `LeaveBalanceIntegrationTest` covering policy CRUD + role / cross-tenant guard, balance auto-init with INITIAL ledger, employee read-self + cross-user guard, adjust with note + ledger + audit, adjust negative guard, annual-leave deduct end-to-end with DEDUCT ledger, insufficient-balance rollback, cross-year rejection, sick/correction non-deduction.

### Web Dashboard

- Added `/leave-balances` page (admin) with year filter, member search, table showing kuota / terpakai / penyesuaian / sisa, remaining badge variants, and "Sesuaikan" modal with year / days / note fields.
- Added sidebar entry "Saldo Cuti" (Sparkles icon).
- `/leave-requests` row Periode column now shows the day count for `ANNUAL_LEAVE`.
- Added `LeavePolicy`, `LeaveBalance`, `UpdateLeavePolicyRequest`, `AdjustLeaveBalanceRequest` types; new endpoints, services, and hooks (`useLeavePolicy`, `useUpdateLeavePolicy`, `useLeaveBalances`, `useMemberLeaveBalance`, `useAdjustLeaveBalance`).

### Mobile

- Added read-only "Sisa Cuti Tahunan" card on the Profile screen showing `Sisa N hari dari Q hari` + `Tahun YYYY • Terpakai X hari`. Uses `GET /members/{userId}/leave-balance` with userId derived from JWT `sub`. Errors render "Belum tersedia" without crashing.
- Added `leave_balance` feature folder with domain model and Riverpod repository.

### Docs

- Added `docs/21-leave-balance.md` covering schema, endpoints, deduction flow, idempotency strategy, audit actions, web/mobile UX, and explicit limitations (no accrual, no carry-forward, no half-day, no holiday calendar, no approved-cancel restore, no cross-year, no payroll, no negative balance).
- Updated `README.md` highlights and added pointer to the new doc.

### Notes

- Cancel APPROVED leave is still out of scope; only PENDING can be cancelled. `RESTORE` ledger type is allocated for the future but unused in v1.4.0.
- Mobile balance card is read-only; adjustments and policy edits remain admin-only via web.

## v1.3.2 - Engineering Lifecycle & Release Checklist

### Docs

- Added engineering lifecycle documentation at `docs/19-development-lifecycle.md` covering Planning, Analysis, Implementation Rules, Review, QA, Release, Stabilization Policy, Security & Secret Safety, and a recommended prompt pattern for new phases.
- Added release checklist documentation at `docs/20-release-checklist.md` covering pre-release, validation commands, commit, GitHub Actions, tag & GitHub Release, and post-release steps with explicit CHANGELOG ↔ GitHub Release consistency checks.
- Linked both documents from `README.md` under a new "Engineering Lifecycle" section.

### Notes

- No backend, web, mobile, endpoint, schema, migration, dependency, or screenshot changes.

## v1.3.1 - Correction QA Stabilization

### Docs

- Added correction QA guide at `docs/18-correction-qa-guide.md` covering local setup, demo accounts, Postman variables, happy path, DB verification, idempotency, apply failed concept, and v1.3.1 limitations.

### Postman

- Added `Correction QA Flow` collection with create / approve attendance correction requests and auto-set variables for `correctionLeaveRequestId`.

### Backend

- Added correction apply QA test coverage to harden the v1.3.0 engine against regression. No endpoint, migration, schema, or service behavior changes.

## v1.3.0 - Attendance Correction Apply Engine

### Backend

- Added `attendance_correction_applies` table with original vs applied clock-in/out, status, work duration, reviewer, applied_by, applied_at, and `record_created_by_correction` flag. UNIQUE per `leave_request_id` for idempotency.
- Added correction metadata columns to `attendance_records`: `correction_applied`, `correction_request_id`, `corrected_by`, `corrected_at`, `correction_note`.
- Added `CorrectionApplyService` that runs in the same transaction as leave approval. APPROVED status now means the correction has been applied. If apply fails, the parent transaction rolls back and the request stays PENDING with a 422 response.
- Added `AttendanceStatusCalculator` helper that reuses `ShiftScheduleResolver` to recompute status and work duration from corrected clock-in/out.
- Correction apply preserves original lat/long, device id, location id, face data, and `attendance_attempts` — for existing records only `clock_in_at`/`clock_out_at`/status/work duration and correction metadata are mutated.
- Correction-generated records (no prior attendance) write null lat/long/device/location/face to clearly distinguish from real mobile clock-ins.
- Added audit actions `ATTENDANCE_CORRECTION_APPLIED`, `ATTENDANCE_CORRECTION_ALREADY_APPLIED`, and `ATTENDANCE_CORRECTION_APPLY_FAILED` (committed via REQUIRES_NEW even on rollback). `ATTENDANCE_CORRECTION_APPROVED` is retained as the reviewer-decision event.
- Daily report exposes `correctionApplied`, `correctionRequestId`, and `correctedAt` per row. CSV / Excel / PDF exports add `Correction Applied` and `Correction Request ID` columns at the tail.
- Added `CorrectionApplyIntegrationTest` covering existing-record update, original snapshot, idempotency, new-record creation, non-correction non-mutation, attempts/geofence preservation, daily report indicator, CSV column, audit events, and notification failure tolerance.

### Web Dashboard

- `/attendance` row Status column shows a `"Dikoreksi"` badge when `correctionApplied=true`.
- `/leave-requests` row Catatan column displays "Koreksi ini sudah diterapkan ke data absensi." for `ATTENDANCE_CORRECTION` requests in `APPROVED` status.
- Updated `DailyReportRow` types with the new correction fields.
- Regenerated `docs/images/web-attendance.png` and `docs/images/web-leave-requests.png`.

### Mobile

- No mobile code changes. Backend response is additive; mobile parser ignores the new fields.

### Notes

- Apply correction is triggered automatically by approve; there is no manual apply endpoint.
- Rollback of an applied correction requires manual DB operation using the diff in `attendance_correction_applies`.
- Payroll, leave balance / accrual, holiday calendar, attachment upload, real face recognition, and manager-tier reviewer hierarchy remain out of scope.

## v1.2.0 - Leave / Permission Request Foundation

### Backend

- Added `leave_requests` table with request type, status, reviewer, and optional correction timestamps.
- Added tenant-scoped endpoints: list, create, get, approve, reject, cancel leave requests.
- Added validation: required reason for non-correction types, overlap check for non-correction pending/approved requests, 31-day range cap, minimum one clock-in/out time for `ATTENDANCE_CORRECTION`.
- Reviewer roles limited to `TENANT_ADMIN` and `SUPER_ADMIN`; requester roles `EMPLOYEE`, `STUDENT`, `TEACHER`, `MANAGER`, `TENANT_ADMIN`.
- Added notification event types `LEAVE_REQUEST_CREATED`, `LEAVE_REQUEST_APPROVED`, `LEAVE_REQUEST_REJECTED`, `LEAVE_REQUEST_CANCELLED` with templates and IN_APP/EMAIL/PUSH defaults.
- Notification publish failure is swallowed and does not break leave flow.
- Added audit actions `LEAVE_REQUEST_CREATED`, `LEAVE_REQUEST_APPROVED`, `LEAVE_REQUEST_REJECTED`, `LEAVE_REQUEST_CANCELLED`, and `ATTENDANCE_CORRECTION_APPROVED`.
- Daily report overlays approved leave info per row, including leave-only rows for users with no attendance record.
- CSV / Excel / PDF exports append `Leave Type` and `Leave Status` columns; leave-only rows emit empty attendance fields.
- Added `LeaveRequestIntegrationTest` covering self-create, role isolation, list scoping, cross-tenant rejection, approve, reject, cancel, invalid range, overlap, correction validation, report overlay, export columns, and notification failure tolerance.

### Web Dashboard

- Added `/leave-requests` page with status/type/date filters, table, badges (Sakit / Izin / Cuti / Dinas luar / Koreksi absensi), approve/reject/cancel actions, review note field, and empty state "Belum ada pengajuan izin.".
- Added sidebar entry `Pengajuan` (ClipboardCheck icon).
- Attendance page row status now shows leave badge when approved leave overlays the row; rows without attendance still appear when only leave exists.
- Added `web-leave-requests.png` screenshot via `npm run screenshots`.

### Mobile

- Added `Pengajuan` bottom navigation tab with list of own leave requests, pull-to-refresh, status chip, and cancel action for PENDING.
- Added create leave request form with type dropdown, date pickers, optional clock-in/out pickers for correction, and reason field with non-correction validation.

### Notes

- Attendance correction approval is **not** payroll-grade in v1.2.0: `attendance_records` are not mutated, and approved corrections appear only as overlay on reports/exports. See `docs/16-leave-permission.md` for the limitation rationale.
- Leave balance, accrual, holiday calendar, attachment upload, payroll integration, parent self-request, manager hierarchy reviewer, real face recognition, and shift swap requests are not included.

## v1.1.0 - Shift & Flexible Schedule

### Backend

- Added `shift_templates` and `member_shift_assignments` tables.
- Added shift snapshot columns to `attendance_records` for stable report history.
- Added tenant-scoped shift template and member shift assignment endpoints.
- Added shift schedule resolver with fallback to tenant attendance settings.
- Added simple overnight shift handling for assigned shifts.
- Updated clock-in late calculation to use assigned shift threshold when available.
- Added shift info to daily reports and CSV/Excel/PDF exports.
- Added audit actions `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_ASSIGNMENT_CREATED`, and `SHIFT_ASSIGNMENT_UPDATED`.
- Added backend integration coverage for shift access, assignment overlap, fallback schedule, assigned shift late threshold, overnight resolver, and audit log coverage.

### Web Dashboard

- Added `/shifts` page for shift template management and member assignment.
- Added Shift navigation item.
- Added current shift information to Members and Attendance tables.
- Added screenshot capture targets for Members and Shifts.

### Notes

- Members without shift assignment continue using tenant attendance settings.
- Payroll, leave management, holiday calendars, roster generation, shift swap requests, real biometric verification, and complex overtime approval are not included.

## v1.0.0 - Subscription Payment Foundation

### Backend

- Added `subscription_packages` and `payment_records` tables for tenant subscription payment foundation.
- Added mock payment provider as the default provider for local development and CI.
- Added optional Midtrans Snap payment gateway using backend-side Snap transaction creation.
- Added tenant-scoped endpoints for package catalog, payment creation, payment list, and payment detail.
- Added public Midtrans webhook endpoint with signature verification, amount validation, status mapping, sanitized raw webhook storage, and idempotent status handling.
- Added subscription activation from backend webhook processing only.
- Added audit actions `PAYMENT_CREATED`, `PAYMENT_WEBHOOK_RECEIVED`, `PAYMENT_STATUS_UPDATED`, `SUBSCRIPTION_ACTIVATED`, and `PAYMENT_WEBHOOK_IGNORED`.
- Added backend integration tests for payment creation, role guard, cross-tenant rejection, webhook activation, duplicate webhook idempotency, invalid signature, amount mismatch, late webhook behavior, raw webhook exposure, and audit log coverage.

### Web Dashboard

- Updated Subscription page with package selection from backend, create payment action, payment URL button, and payment history.
- Added payment status badges for `PENDING`, `PAID`, `FAILED`, `EXPIRED`, and `CANCELLED`.
- Refreshed Subscription screenshot.

### Notes

- Midtrans Snap is optional and disabled by default.
- CI and local development use the mock provider and do not require Midtrans keys.
- Subscription status is not activated from frontend callbacks; activation happens from verified backend webhook handling.
- Refund, recurring billing automation, proration, invoice PDF, payment email, and settlement dashboard are not included.

## v0.9.0 - Advanced Export PDF and Excel Reports

### Backend

- Added Apache POI based Excel export for attendance reports.
- Added OpenPDF based PDF export for attendance reports.
- Added tenant-scoped endpoints `GET /api/v1/tenants/{tenantId}/reports/attendance/export.xlsx` and `GET /api/v1/tenants/{tenantId}/reports/attendance/export.pdf`.
- Reused the CSV export data mapping so CSV, XLSX, and PDF reports stay consistent.
- Kept the existing export validation: required `from`/`to`, `from <= to`, tenant access guard, and maximum 31-day range.
- Added audit actions `REPORT_EXCEL_EXPORTED` and `REPORT_PDF_EXPORTED`.
- Added backend integration coverage for XLSX/PDF success, invalid ranges, cross-tenant rejection, response headers, non-empty bodies, and audit log entries.

### Web Dashboard

- Added `Unduh Excel` and `Unduh PDF` buttons to the Attendance page beside `Unduh CSV`.
- Downloads use the same date filters and authenticated blob download flow as CSV.
- Added friendly download failure copy.
- Refreshed the Attendance screenshot.

### Notes

- Excel is intended for admin operations and analysis.
- PDF is intended for formal attendance reports.
- Export remains MVP-scoped to 31 days per request and does not include scheduler, email reports, template editor, permanent file storage, or large streaming export.

## v0.8.0 - Real Notification Providers

### Backend

- Added optional Resend email provider for notification gateway.
- Added optional Firebase Cloud Messaging push provider for notification gateway.
- Kept mock/log-only email and push providers as the default fallback.
- Added `notification_device_tokens` table for FCM token registration.
- Added tenant-scoped endpoint `POST /api/v1/tenants/{tenantId}/notification-tokens`.
- Added masked notification destinations in delivery logs for email and FCM tokens.
- Added backend tests for token registration, non-member access denial, skipped push delivery without tokens, and masked FCM token destinations.

### Mobile App

- Added optional Firebase Messaging dependencies.
- Added guarded Firebase initialization controlled by `HADIVO_ENABLE_FIREBASE_MESSAGING`.
- Added push token registration after login/session restore when Firebase is configured.
- Sends existing privacy-friendly `deviceId` with notification token registration.

### Web Dashboard

- Added provider badges for notification delivery logs.

### Notes

- Real providers are opt-in and disabled by default.
- Missing Resend or FCM configuration falls back to mock/log-only behavior.
- Do not commit Resend API keys, Firebase service account files, `google-services.json`, FCM tokens, or credential files.
- Notification delivery failures remain best effort and do not fail attendance actions.

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
