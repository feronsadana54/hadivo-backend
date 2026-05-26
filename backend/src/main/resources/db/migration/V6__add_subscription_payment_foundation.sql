create table subscription_packages (
    id                uuid primary key default gen_random_uuid(),
    code              varchar(60) not null unique,
    name              varchar(120) not null,
    plan              varchar(20) not null,
    billing_period    varchar(20) not null,
    gross_amount      numeric(19, 2) not null check (gross_amount >= 0),
    currency          varchar(3) not null default 'IDR',
    duration_months   integer not null check (duration_months > 0),
    active            boolean not null default true,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
create index idx_subscription_packages_active on subscription_packages(active, plan, billing_period);

insert into subscription_packages (id, code, name, plan, billing_period, gross_amount, currency, duration_months)
values
    ('22222222-2222-2222-2222-222222222201', 'PRO_MONTHLY', 'Hadivo Pro Bulanan', 'PRO', 'MONTHLY', 99000.00, 'IDR', 1),
    ('22222222-2222-2222-2222-222222222202', 'PRO_YEARLY', 'Hadivo Pro Tahunan', 'PRO', 'YEARLY', 990000.00, 'IDR', 12),
    ('22222222-2222-2222-2222-222222222301', 'BUSINESS_MONTHLY', 'Hadivo Business Bulanan', 'BUSINESS', 'MONTHLY', 299000.00, 'IDR', 1),
    ('22222222-2222-2222-2222-222222222302', 'BUSINESS_YEARLY', 'Hadivo Business Tahunan', 'BUSINESS', 'YEARLY', 2990000.00, 'IDR', 12),
    ('22222222-2222-2222-2222-222222222401', 'ENTERPRISE_MONTHLY', 'Hadivo Enterprise Bulanan', 'ENTERPRISE', 'MONTHLY', 999000.00, 'IDR', 1),
    ('22222222-2222-2222-2222-222222222402', 'ENTERPRISE_YEARLY', 'Hadivo Enterprise Tahunan', 'ENTERPRISE', 'YEARLY', 9990000.00, 'IDR', 12)
on conflict (id) do nothing;

create table payment_records (
    id                        uuid primary key default gen_random_uuid(),
    tenant_id                 uuid not null references tenants(id) on delete cascade,
    package_id                uuid references subscription_packages(id),
    subscription_id           uuid references subscriptions(id),
    provider                  varchar(30) not null,
    provider_order_id         varchar(50) not null unique,
    provider_transaction_id   varchar(120),
    payment_url               text,
    gross_amount              numeric(19, 2) not null,
    currency                  varchar(3) not null default 'IDR',
    status                    varchar(20) not null,
    paid_at                   timestamptz,
    expired_at                timestamptz,
    raw_webhook_json          text,
    created_by                uuid references users(id) on delete set null,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now()
);
create index idx_payment_records_tenant_created on payment_records(tenant_id, created_at desc);
create index idx_payment_records_tenant_status on payment_records(tenant_id, status);
