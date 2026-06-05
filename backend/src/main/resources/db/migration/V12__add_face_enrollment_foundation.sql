create table user_face_profiles (
    id                   uuid primary key default gen_random_uuid(),
    tenant_id            uuid not null references tenants(id) on delete cascade,
    user_id              uuid not null references users(id) on delete cascade,
    enrollment_status    varchar(20) not null default 'PENDING',
    consent_given        boolean not null default false,
    consent_given_at     timestamptz,
    image_reference      varchar(255),
    embedding_reference  varchar(255),
    enrolled_at          timestamptz,
    reset_at             timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint uniq_face_profiles_tenant_user unique (tenant_id, user_id)
);

create index idx_face_profiles_tenant on user_face_profiles(tenant_id);
create index idx_face_profiles_user on user_face_profiles(user_id);
