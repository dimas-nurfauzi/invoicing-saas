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

Swagger UI: <http://localhost:8080/swagger-ui.html> (spec at `/v3/api-docs`).
Register a tenant, call login, click **Authorize** and paste the `accessToken`.
Disable both in production with `SPRINGDOC_ENABLED=false`.

Or with curl:

```bash
curl -X POST localhost:8080/api/tenants -H 'Content-Type: application/json' \
  -d '{"tenantName":"Toko Maju","tenantSlug":"toko-maju","adminEmail":"owner@tokomaju.id","adminPassword":"rahasia-123"}'
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"toko-maju","email":"owner@tokomaju.id","password":"rahasia-123"}'
curl localhost:8080/api/me -H "Authorization: Bearer <accessToken>"
curl localhost:8080/api/users -H "Authorization: Bearer <accessToken>"
curl -X POST localhost:8080/api/users -H "Authorization: Bearer <accessToken>" \
  -H 'Content-Type: application/json' \
  -d '{"email":"staff@tokomaju.id","password":"rahasia-456","role":"STAFF"}'
```

## Customers

`/api/customers`: create, list (paged, `q` searches name/email), get, update
(`PUT`), archive (`DELETE`, ADMIN only) and restore. Archived customers are
hidden from lists unless `includeArchived=true`, but stay readable by id so
invoices can keep referencing them.

## Invoices

`/api/invoices`: create a draft for a customer with 1–100 lines (quantity, unit
price, discount %, tax %), list (paged, filter by `status` / `customerId`), get,
replace (`PUT`, drafts only) and delete (drafts only). Totals are computed with
`BigDecimal`, rounded HALF_UP to 2 decimals per line and then summed; money is
returned as strings.

- `POST /api/invoices/{id}/issue`: DRAFT → SENT, assigns the next number
  `INV-<year>-NNNN` (per tenant and year, gapless and unique under concurrency
  via a row-locked counter) and freezes the customer's billing data.
- `POST /api/invoices/{id}/void` (ADMIN, with a reason): SENT/OVERDUE → VOID.
  The number stays used.

## Payments

`POST /api/invoices/{id}/payments` records a payment (amount, date, method,
reference) against a SENT or OVERDUE invoice; partial payments are fine and the
invoice becomes PAID when its balance reaches zero. Overpayment is rejected.
The invoice row is locked while a payment is recorded, so concurrent payments
can't overpay. `GET /api/invoices/{id}/payments` lists them. Payments are
append-only for now.

## Overdue job and summary

A daily job (`OVERDUE_CRON`, default 00:05 in `OVERDUE_ZONE`, default
Asia/Jakarta) marks SENT invoices past their due date as OVERDUE. It runs per
tenant through the normal tenant filter and locks the rows it changes, so a
payment arriving at the same moment is never overwritten.

`GET /api/summary?from=&to=` (optional issue-date range) returns, per currency:
total billed, paid, outstanding and overdue, plus invoice counts per status.
Drafts and voided invoices are not billed.

## AI invoice drafts

`POST /api/invoices/ai-draft` with `{"text": "Tagih PT Sinar Terang: desain logo
1,5jt, PPN 11%, jatuh tempo 30 hari"}` returns a **draft** (nothing is saved):
customer (matched by exact name within your tenant), dates, currency, lines, a
preview total and warnings for anything it had to assume. Review it and send it
to `POST /api/invoices` to save it, with the same validation as manual entry.

Under the hood: Claude (`claude-opus-5-5`, effort `low`, via the official Java
SDK) with structured outputs constrained to `ai/invoice-draft.schema.json`. The
output is treated as untrusted: parsed, validated against the same JSON schema,
then against the manual-entry line rules; invalid output is retried (3 attempts
in total) with the errors in the prompt, then the endpoint fails with 502.
Refusals return 422 (after the API's server-side fallback). Only the pasted text
and today's date are sent; customer data never is. Set `ANTHROPIC_API_KEY` to
enable it (503 without it); tests use a scripted fake and never call the API.

## Platform admin

The SaaS operator has a separate account type (not a tenant role). Set
`PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` (12+ chars) and the first
admin is created at startup. `POST /api/platform/auth/login` returns a platform
token that works only under `/api/platform/**`:

- list / view tenants, create a tenant (also when `TENANT_SIGNUP_ENABLED=false`)
- suspend / activate a tenant: a suspended tenant can't log in and its existing
  tokens are rejected immediately

Platform admins can't read tenant business data on their own, and tenant tokens
can't call the platform API.

### Support access

When a tenant needs help, its ADMIN grants time-limited access
(`POST /api/support-access`, 1–72 hours) and can revoke it any time. A platform
admin can then read that tenant's data through `/api/platform/tenants/{id}/support/...`
with a required `reason` (e.g. a ticket number). Reads are read-only at the
database level, and every access is logged; the tenant sees the log at
`GET /api/support-access/logs`.

Every response carries an `X-Request-Id`; log lines include `req=`, `tenant=`
and `user=` so a support ticket can be traced without opening tenant data.

## Multi-tenancy

The tenant comes only from the signed JWT (`tenant_id` claim), never from the
request. Isolation is enforced centrally by Hibernate's `@TenantId`: every
query on a tenant-owned entity is filtered by the current tenant, inserts are
stamped with it, and with no tenant in context nothing matches (fail closed).
Another tenant's id returns the same 404 as an id that doesn't exist.
`TenantIsolationTests` checks read / list / update / delete across tenants.

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
- [x] Week 1 — tenants, users, JWT auth, centralized tenant filter, isolation tests
- [x] Week 2 — customers, invoices, line items, tax, discount
- [x] Week 3 — partial payments, invoice numbering, overdue job, summary
- [x] Week 4 — AI draft extraction, troubleshooting write-ups

## Troubleshooting write-ups

What went wrong (or could have), how it was designed out, and the tests that
prove it, including mutation checks where the fix was removed to show the test
fails:

- [Preventing a cross-tenant data leak](docs/troubleshooting/cross-tenant-data-leak.md):
  `@TenantId` vs `@Filter` vs RLS, fail-closed tenant context, 404 not 403,
  isolation tests that fail 7/7 when the filter is bypassed.
- [Race conditions: invoice numbering and payments](docs/troubleshooting/invoice-numbering-race-condition.md):
  why `MAX()+1` and sequences don't give gapless numbers, a row-locked counter,
  and a lost update in partial payments that a CHECK constraint didn't catch.
- [Money precision and tax rounding](docs/troubleshooting/money-precision-and-rounding.md):
  `BigDecimal` end to end, HALF_UP per line then summed (and the case where it
  changes the total), money as JSON strings.
