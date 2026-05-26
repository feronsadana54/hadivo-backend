create table notification_device_tokens (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    user_id         uuid        not null references users(id)   on delete cascade,
    device_id       varchar(160),
    fcm_token       text        not null,
    platform        varchar(60),
    active          boolean     not null default true,
    last_seen_at    timestamptz not null default now(),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    check (length(trim(fcm_token)) > 0)
);

create unique index uq_notification_device_tokens_token
    on notification_device_tokens(fcm_token);
create index idx_notification_device_tokens_tenant_user
    on notification_device_tokens(tenant_id, user_id);
create index idx_notification_device_tokens_active_user
    on notification_device_tokens(tenant_id, user_id)
    where active = true;
