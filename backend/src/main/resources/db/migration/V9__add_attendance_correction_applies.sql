create table attendance_correction_applies (
    id                              uuid primary key default gen_random_uuid(),
    tenant_id                       uuid not null references tenants(id) on delete cascade,
    leave_request_id                uuid not null unique references leave_requests(id) on delete cascade,
    attendance_record_id            uuid references attendance_records(id) on delete set null,
    requester_user_id               uuid not null references users(id) on delete cascade,
    reviewer_user_id                uuid not null references users(id) on delete cascade,
    applied_by                      uuid not null references users(id) on delete cascade,
    original_clock_in_at            timestamptz,
    original_clock_out_at           timestamptz,
    applied_clock_in_at             timestamptz,
    applied_clock_out_at            timestamptz,
    original_status                 varchar(40),
    applied_status                  varchar(40) not null,
    original_work_duration_minutes  integer,
    applied_work_duration_minutes   integer,
    correction_reason               text,
    record_created_by_correction    boolean not null default false,
    applied_at                      timestamptz not null default now(),
    created_at                      timestamptz not null default now()
);
create index idx_correction_applies_tenant on attendance_correction_applies(tenant_id);
create index idx_correction_applies_record on attendance_correction_applies(attendance_record_id);

alter table attendance_records
    add column correction_applied    boolean not null default false,
    add column correction_request_id uuid references leave_requests(id) on delete set null,
    add column corrected_by          uuid references users(id) on delete set null,
    add column corrected_at          timestamptz,
    add column correction_note       text;
