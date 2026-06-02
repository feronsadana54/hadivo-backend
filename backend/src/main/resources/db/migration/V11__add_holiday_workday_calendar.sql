create table tenant_workday_settings (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid not null unique references tenants(id) on delete cascade,
    monday_workday      boolean not null default true,
    tuesday_workday     boolean not null default true,
    wednesday_workday   boolean not null default true,
    thursday_workday    boolean not null default true,
    friday_workday      boolean not null default true,
    saturday_workday    boolean not null default false,
    sunday_workday      boolean not null default false,
    active              boolean not null default true,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_tenant_workday_settings_tenant on tenant_workday_settings(tenant_id);

create table tenant_holidays (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid not null references tenants(id) on delete cascade,
    holiday_date    date not null,
    name            varchar(120) not null,
    type            varchar(20) not null default 'CUSTOM',
    active          boolean not null default true,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint uniq_tenant_holidays_date_name unique (tenant_id, holiday_date, name)
);

create index idx_tenant_holidays_tenant on tenant_holidays(tenant_id);
create index idx_tenant_holidays_date on tenant_holidays(holiday_date);
create index idx_tenant_holidays_active_lookup on tenant_holidays(tenant_id, active, holiday_date);
