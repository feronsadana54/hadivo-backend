create table shift_templates (
    id                       uuid primary key default gen_random_uuid(),
    tenant_id                uuid not null references tenants(id) on delete cascade,
    name                     varchar(120) not null,
    start_time               time not null,
    end_time                 time not null,
    late_threshold_minutes   integer not null default 0 check (late_threshold_minutes >= 0 and late_threshold_minutes <= 240),
    allows_overtime          boolean not null default false,
    active                   boolean not null default true,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now()
);
create index idx_shift_templates_tenant on shift_templates(tenant_id);
create index idx_shift_templates_tenant_active on shift_templates(tenant_id, active);

create table member_shift_assignments (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid not null references tenants(id) on delete cascade,
    user_id             uuid not null references users(id) on delete cascade,
    shift_template_id   uuid not null references shift_templates(id) on delete restrict,
    effective_from      date not null,
    effective_to        date,
    active              boolean not null default true,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    foreign key (tenant_id, user_id) references memberships(tenant_id, user_id) on delete cascade,
    check (effective_to is null or effective_to >= effective_from)
);
create index idx_member_shift_assignments_tenant on member_shift_assignments(tenant_id);
create index idx_member_shift_assignments_user on member_shift_assignments(tenant_id, user_id, active, effective_from, effective_to);
create index idx_member_shift_assignments_shift on member_shift_assignments(shift_template_id);

alter table attendance_records
    add column shift_template_id uuid references shift_templates(id) on delete set null,
    add column shift_name varchar(120),
    add column scheduled_start_time time,
    add column scheduled_end_time time,
    add column late_threshold_minutes integer;
