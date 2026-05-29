create table leave_requests (
    id                          uuid primary key default gen_random_uuid(),
    tenant_id                   uuid not null references tenants(id) on delete cascade,
    requester_user_id           uuid not null references users(id) on delete cascade,
    request_type                varchar(40) not null,
    start_date                  date not null,
    end_date                    date not null,
    reason                      text,
    status                      varchar(20) not null default 'PENDING',
    reviewer_user_id            uuid references users(id) on delete set null,
    reviewed_at                 timestamptz,
    review_note                 text,
    attachment_url              text,
    requested_clock_in_at       timestamptz,
    requested_clock_out_at      timestamptz,
    correction_note             text,
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now(),
    check (end_date >= start_date)
);

create index idx_leave_requests_tenant on leave_requests(tenant_id);
create index idx_leave_requests_tenant_requester on leave_requests(tenant_id, requester_user_id, status);
create index idx_leave_requests_tenant_status_dates on leave_requests(tenant_id, status, start_date, end_date);
