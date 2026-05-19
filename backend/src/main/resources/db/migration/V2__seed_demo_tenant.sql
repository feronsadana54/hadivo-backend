-- Demo tenant for local development. The application's DataSeeder
-- creates the super admin and tenant admin users (BCrypt password
-- is configurable via SEED_SUPER_ADMIN_PASSWORD).

insert into tenants (id, name, slug, mode, timezone, active)
values (
    '11111111-1111-1111-1111-111111111111',
    'Hadivo Demo School',
    'hadivo-demo',
    'SCHOOL',
    'Asia/Jakarta',
    true
);

insert into subscriptions (tenant_id, plan, max_members, started_at, status)
values (
    '11111111-1111-1111-1111-111111111111',
    'FREE',
    10,
    now(),
    'ACTIVE'
);

insert into tenant_attendance_settings (tenant_id)
values ('11111111-1111-1111-1111-111111111111');

insert into tenant_locations (
    id, tenant_id, name, latitude, longitude, radius_meters, active
) values (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Kampus Utama',
    -6.200000,
    106.816666,
    100,
    true
);
