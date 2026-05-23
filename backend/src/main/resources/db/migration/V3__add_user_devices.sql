create table user_devices (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid         not null references tenants(id) on delete cascade,
    user_id         uuid         not null references users(id)   on delete cascade,
    device_id       varchar(120) not null,
    device_name     varchar(120),
    platform        varchar(60),
    trusted         boolean      not null default true,
    active          boolean      not null default true,
    first_seen_at   timestamptz  not null,
    last_seen_at    timestamptz  not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    check (length(trim(device_id)) > 0)
);

create index idx_user_devices_tenant_user on user_devices(tenant_id, user_id);
create index idx_user_devices_device_id on user_devices(device_id);

create unique index uq_user_devices_active_trusted_user
    on user_devices(tenant_id, user_id)
    where active = true and trusted = true;
