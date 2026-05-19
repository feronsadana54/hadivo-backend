# ADR 0003: Use PostgreSQL as the primary database

- Status: Accepted
- Date: 2026-05-19

## Context

Domain absensi transaksional: butuh konsistensi (satu record per user per hari), constraint (UNIQUE, FK), dan query agregasi sederhana untuk laporan. Data multi-tenant tapi tidak super besar di tahap awal (≤ 500 anggota per tenant).

## Decision

Gunakan PostgreSQL 16 sebagai single source of truth. Multi-tenancy dengan shared schema + kolom `tenant_id` (bukan schema-per-tenant maupun database-per-tenant).

Fitur Postgres yang dipakai:

- UUID v4 sebagai PK (`gen_random_uuid()` dari `pgcrypto`).
- `jsonb` untuk `notifications.payload_json` dan `audit_logs.metadata_json`.
- Partial index untuk notifikasi unread.
- Constraint UNIQUE komposit untuk mencegah double clock-in.

## Consequences

- Stack stabil, well-supported di Spring Boot via spring-data-jpa + Flyway.
- Multi-tenancy via kolom = paling fleksibel untuk laporan lintas-tenant (Fase 2) tanpa migrasi schema.
- Risiko keamanan: setiap query harus filter `tenant_id`. Mitigasi: semua repository menerima `tenantId` sebagai parameter dan `MembershipGuard` mem-validasi auth principal di controller.
- Saat suatu tenant tumbuh sangat besar, table partition (declarative partitioning by `tenant_id`) bisa ditambahkan tanpa mengubah API.
