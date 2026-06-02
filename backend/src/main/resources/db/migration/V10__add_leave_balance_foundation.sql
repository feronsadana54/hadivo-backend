create table leave_policies (
    id                              uuid primary key default gen_random_uuid(),
    tenant_id                       uuid not null unique references tenants(id) on delete cascade,
    name                            varchar(80) not null default 'Default',
    annual_leave_quota_days         numeric(6,2) not null default 12,
    sick_leave_requires_balance     boolean not null default false,
    permission_requires_balance     boolean not null default false,
    business_trip_requires_balance  boolean not null default false,
    active                          boolean not null default true,
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now(),
    check (annual_leave_quota_days >= 0 and annual_leave_quota_days <= 365)
);

create index idx_leave_policies_tenant on leave_policies(tenant_id);

create table leave_balances (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid not null references tenants(id) on delete cascade,
    user_id             uuid not null references users(id) on delete cascade,
    year                int not null,
    annual_quota_days   numeric(8,2) not null,
    used_days           numeric(8,2) not null default 0,
    adjusted_days       numeric(8,2) not null default 0,
    remaining_days      numeric(8,2) not null,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint uniq_leave_balance_tenant_user_year unique (tenant_id, user_id, year)
);

create index idx_leave_balances_tenant on leave_balances(tenant_id);
create index idx_leave_balances_user on leave_balances(user_id);
create index idx_leave_balances_year on leave_balances(year);

create table leave_balance_ledgers (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid not null references tenants(id) on delete cascade,
    user_id             uuid not null references users(id) on delete cascade,
    leave_request_id    uuid references leave_requests(id) on delete set null,
    year                int not null,
    change_type         varchar(20) not null,
    days_changed        numeric(8,2) not null,
    balance_before      numeric(8,2) not null,
    balance_after       numeric(8,2) not null,
    note                text,
    created_by          uuid references users(id) on delete set null,
    created_at          timestamptz not null default now()
);

create index idx_leave_balance_ledgers_tenant on leave_balance_ledgers(tenant_id);
create index idx_leave_balance_ledgers_user on leave_balance_ledgers(user_id);
create index idx_leave_balance_ledgers_request on leave_balance_ledgers(leave_request_id);

-- Partial unique index: each leave request may produce at most one DEDUCT ledger.
-- Defence-in-depth for idempotency together with the leave_request status guard.
create unique index uniq_leave_balance_ledger_deduct_per_request
    on leave_balance_ledgers(leave_request_id)
    where change_type = 'DEDUCT';
