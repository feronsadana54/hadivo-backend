create extension if not exists "pgcrypto";

create table tenants (
    id              uuid primary key default gen_random_uuid(),
    name            varchar(120) not null,
    slug            varchar(60)  not null unique,
    mode            varchar(20)  not null,
    timezone        varchar(60)  not null default 'Asia/Jakarta',
    active          boolean      not null default true,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create table users (
    id              uuid primary key default gen_random_uuid(),
    email           varchar(160) not null unique,
    password_hash   varchar(255) not null,
    full_name       varchar(120) not null,
    phone           varchar(30),
    active          boolean      not null default true,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create table memberships (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid         not null references tenants(id) on delete cascade,
    user_id         uuid         not null references users(id)   on delete cascade,
    role            varchar(20)  not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    unique (tenant_id, user_id)
);
create index idx_memberships_tenant on memberships(tenant_id);
create index idx_memberships_user   on memberships(user_id);

create table parent_student_links (
    id                uuid primary key default gen_random_uuid(),
    tenant_id         uuid not null references tenants(id) on delete cascade,
    parent_user_id    uuid not null references users(id)   on delete cascade,
    student_user_id   uuid not null references users(id)   on delete cascade,
    relationship      varchar(40) not null,
    active            boolean      not null default true,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    unique (tenant_id, parent_user_id, student_user_id),
    check (parent_user_id <> student_user_id)
);
create index idx_parent_links_tenant_parent on parent_student_links(tenant_id, parent_user_id);
create index idx_parent_links_tenant_student on parent_student_links(tenant_id, student_user_id);

create table subscriptions (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid         not null references tenants(id) on delete cascade,
    plan            varchar(20)  not null,
    max_members     integer      not null,
    started_at      timestamptz  not null,
    expires_at      timestamptz,
    status          varchar(20)  not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);
create index idx_subscriptions_tenant_status on subscriptions(tenant_id, status);

create table tenant_locations (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid         not null references tenants(id) on delete cascade,
    name            varchar(120) not null,
    latitude        double precision not null,
    longitude       double precision not null,
    radius_meters   integer      not null check (radius_meters > 0),
    active          boolean      not null default true,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);
create index idx_tenant_locations_tenant on tenant_locations(tenant_id);

create table tenant_attendance_settings (
    tenant_id                       uuid primary key references tenants(id) on delete cascade,
    require_face_clock_in           boolean not null default false,
    require_face_clock_out          boolean not null default false,
    allow_clock_out_outside_radius  boolean not null default false,
    allow_late_clock_in             boolean not null default true,
    work_start_time                 time    not null default '08:00',
    work_end_time                   time    not null default '17:00',
    late_threshold_minutes          integer not null default 15,
    timezone                        varchar(60) not null default 'Asia/Jakarta',
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now()
);

create table attendance_records (
    id                          uuid primary key default gen_random_uuid(),
    tenant_id                   uuid not null references tenants(id) on delete cascade,
    user_id                     uuid not null references users(id)   on delete cascade,
    date                        date not null,
    clock_in_at                 timestamptz,
    clock_out_at                timestamptz,
    clock_in_location_id        uuid references tenant_locations(id),
    clock_out_location_id       uuid references tenant_locations(id),
    clock_in_latitude           double precision,
    clock_in_longitude          double precision,
    clock_out_latitude          double precision,
    clock_out_longitude         double precision,
    clock_in_device_id          varchar(120),
    clock_out_device_id         varchar(120),
    clock_out_outside_radius    boolean not null default false,
    status                      varchar(20) not null,
    work_duration_minutes       integer,
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now(),
    unique (tenant_id, user_id, date)
);
create index idx_attendance_records_tenant_date on attendance_records(tenant_id, date);
create index idx_attendance_records_user_date   on attendance_records(user_id, date);

create table attendance_attempts (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid not null references tenants(id) on delete cascade,
    user_id         uuid not null references users(id)   on delete cascade,
    type            varchar(20) not null,
    reason          varchar(40) not null,
    latitude        double precision,
    longitude       double precision,
    device_id       varchar(120),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_attendance_attempts_tenant_user on attendance_attempts(tenant_id, user_id);
create index idx_attendance_attempts_created     on attendance_attempts(created_at);

create table notifications (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid not null references tenants(id) on delete cascade,
    recipient_user_id   uuid not null references users(id)   on delete cascade,
    type                varchar(40) not null,
    payload_json        jsonb       not null,
    read_at             timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);
create index idx_notifications_recipient_unread
    on notifications(recipient_user_id) where read_at is null;

create table audit_logs (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid references tenants(id) on delete set null,
    actor_user_id   uuid references users(id)   on delete set null,
    action          varchar(80) not null,
    resource_type   varchar(80) not null,
    resource_id     varchar(120),
    metadata_json   jsonb,
    created_at      timestamptz not null default now()
);
create index idx_audit_logs_tenant on audit_logs(tenant_id, created_at desc);
create index idx_audit_logs_actor  on audit_logs(actor_user_id, created_at desc);

create table refresh_tokens (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references users(id) on delete cascade,
    token_hash      varchar(255) not null unique,
    expires_at      timestamptz  not null,
    revoked_at      timestamptz,
    created_at      timestamptz  not null default now()
);
create index idx_refresh_tokens_user on refresh_tokens(user_id);
