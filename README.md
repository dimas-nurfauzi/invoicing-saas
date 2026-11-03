# invoicing-saas

Multi-tenant invoicing API built with Java 21 and Spring Boot 3 — developed
with an AI coding agent (Claude Code), with every change going through a
branch and pull request.

> 🚧 Work in progress. See the roadmap below.

## Why this repo exists

This is a portfolio project showing how I work with an AI coding agent:
what I direct it to do, what I review and correct, and how I debug the
problems that come up. Each PR description records both.

## Features (MVP)

- Tenant registration and JWT login (admin / staff)
- Customers, invoices with line items, tax and discount
- Invoice status: draft, sent, paid, overdue (scheduled job)
- Partial payments
- Sequential invoice numbers per tenant (`INV-2026-0001`)
- Billing summary: billed, paid, outstanding
- AI: paste a billing request → Claude extracts a draft invoice (schema-validated,
  reviewed before saving)

## Stack

Java 21 · Spring Boot 3 · Spring Security + JWT · PostgreSQL · Flyway ·
JUnit 5 + Testcontainers · Docker Compose · GitHub Actions · Actuator + Micrometer

## Running locally

Prerequisites: JDK 21, Docker.

```bash
cp .env.example .env
docker compose up -d        # PostgreSQL 17 on localhost:5433
set -a; source .env; set +a # export JWT_SECRET etc. into this shell
./mvnw spring-boot:run
curl localhost:8080/actuator/health
./mvnw verify               # Spotless lint + tests (Testcontainers)
```

`JWT_SECRET` is required (at least 32 bytes); the app refuses to start
without it. The app does not read `.env` itself, so export it first — or,
in IntelliJ, add `JWT_SECRET=...` under Run Configuration → Environment
variables. Tests use their own value from `src/test/resources/application-test.yml`.

### Trying the API

```bash
curl -X POST localhost:8080/api/tenants -H 'Content-Type: application/json' \
  -d '{"tenantName":"Toko Maju","tenantSlug":"toko-maju","adminEmail":"owner@tokomaju.id","adminPassword":"rahasia-123"}'
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"toko-maju","email":"owner@tokomaju.id","password":"rahasia-123"}'
curl localhost:8080/api/me -H "Authorization: Bearer <accessToken>"
```

PostgreSQL is published on host port **5433** (not 5432) so it can run next
to another local PostgreSQL. Override with `POSTGRES_PORT` / `DB_URL` in `.env`.

### Docker via Colima (macOS)

Testcontainers looks for `/var/run/docker.sock` by default. With Colima,
point it at Colima's socket before running tests:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

(Or put `docker.host=unix:///Users/<you>/.colima/default/docker.sock` in
`~/.testcontainers.properties`.)

## Roadmap

- [x] Step 0 — repo, agent guide (`CLAUDE.md`), PR template
- [x] Step 1 — skeleton, Docker Compose, health endpoint, Testcontainers, CI
- [ ] Week 1 — tenants, users, JWT auth, centralized tenant filter, isolation tests
- [ ] Week 2 — customers, invoices, line items, tax, discount
- [ ] Week 3 — partial payments, invoice numbering, overdue job
- [ ] Week 4 — AI draft extraction, troubleshooting write-ups

## Troubleshooting write-ups

Planned:
- Race condition in invoice numbering
- Cross-tenant data leak
- Money precision and tax rounding
