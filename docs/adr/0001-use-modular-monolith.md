# ADR 0001: Use a modular monolith for the backend

- Status: Accepted
- Date: 2026-05-19

## Context

Tim kecil, requirement masih bisa berubah, dan beban tenant per node masih jauh dari batas vertikal sebuah Postgres + Spring Boot. Memilih microservices di tahap awal berarti membayar biaya operasional (multi-deployment, service discovery, distributed tracing) tanpa manfaat yang seimbang.

## Decision

Backend dibangun sebagai satu artefak Spring Boot dengan modul per domain (`auth`, `tenant`, `attendance`, dst). Setiap modul punya entity, repository, service, controller sendiri. Komunikasi antar-modul lewat service in-process, bukan HTTP. Event antar-modul boleh lewat Spring application event (in-process) atau RabbitMQ kalau memang butuh durability.

## Consequences

- Deploy lebih sederhana — satu jar.
- Refactor batas modul bisa dilakukan via package move tanpa migrasi infra.
- Risiko: tanpa disiplin, modul saling akses repository. Kita batasi via konvensi (modul tidak meng-import repository modul lain) dan code review.
- Saat skala besar atau tim membesar, sebagian modul (mis. `notification`, `face`) bisa dipisah jadi service tersendiri tanpa membongkar yang lain.
