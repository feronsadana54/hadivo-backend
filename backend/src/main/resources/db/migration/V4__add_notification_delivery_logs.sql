create table notification_delivery_logs (
    id                    uuid primary key default gen_random_uuid(),
    tenant_id             uuid references tenants(id) on delete set null,
    recipient_user_id     uuid references users(id) on delete set null,
    channel               varchar(20) not null,
    event_type            varchar(60) not null,
    destination           varchar(180),
    title                 varchar(160) not null,
    body                  text not null,
    status                varchar(20) not null,
    provider              varchar(80),
    provider_message_id   varchar(160),
    error_message         text,
    metadata_json         text,
    created_at            timestamptz not null default now(),
    sent_at               timestamptz
);

create index idx_notification_delivery_logs_tenant_created
    on notification_delivery_logs(tenant_id, created_at desc);
create index idx_notification_delivery_logs_filters
    on notification_delivery_logs(tenant_id, event_type, channel, status);
