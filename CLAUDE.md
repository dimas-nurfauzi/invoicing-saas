# CLAUDE.md

Guidance for AI coding agents (Claude Code) working in this repository.

## Project

Multi-tenant invoicing SaaS. Each tenant (a small business) manages its own
customers and invoices, records payments, and sees a billing summary. One AI
feature: paste a free-text billing request and get a draft invoice to review.

MVP scope:
- Tenant registration, login (roles: `ADMIN`, `STAFF`)
- Customer CRUD
- Invoices with line items, tax, discount
- Status lifecycle: `DRAFT` → `SENT` → `PAID`; `OVERDUE` set by a scheduled job
- Partial payments
- Sequential invoice numbers per tenant per year: `INV-2026-0001`
- Summary: total billed, total paid, total outstanding/overdue
- AI draft extraction (Claude API), validated against a JSON schema, never saved
  without user review

## Stack

- Java 21, Spring Boot 3, Maven (use the wrapper: `./mvnw`)
- Spring Security + JWT (stateless)
- PostgreSQL, Flyway migrations
- JUnit 5, Testcontainers (real PostgreSQL — no H2)
- Docker Compose for local dev
- GitHub Actions: build, test, lint
- Spring Boot Actuator + Micrometer

## Commands

Requires JDK 21 (`JAVA_HOME` pointing at it) and a running Docker daemon.

```bash
cp .env.example .env              # once; local-dev defaults
docker compose up -d              # PostgreSQL 17 on localhost:5433
set -a; source .env; set +a       # export env (JWT_SECRET is required)
./mvnw spring-boot:run            # run the app (http://localhost:8080)
./mvnw verify                     # lint + full build + all tests (needs Docker)
./mvnw test -Dtest=ClassName      # single test class
./mvnw spotless:apply             # auto-format (google-java-format)
./mvnw spotless:check             # lint only (also bound to `validate`)
```

- Health: `GET /actuator/health`, `/actuator/info`, `/actuator/prometheus`.
- Integration tests `@Import(TestcontainersConfiguration.class)` and
  `@ActiveProfiles("test")` (test JWT secret); don't start containers per test class.
- With Colima, Testcontainers needs
  `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` (see README).

## Package layout

Package by feature, not by layer:

```
io.github.dimasnurfauzi.invoicing
├── tenant/      # tenant registration, TenantContext, tenant filter
├── auth/        # JWT, login, security config
├── customer/
├── invoice/     # invoice, line item, numbering, status job
├── payment/
├── summary/
├── ai/          # Claude API client, schema validation, draft extraction
├── platform/    # platform admin (SaaS operator): login, tenant list/suspend
├── supportaccess/ # tenant-granted, audited, read-only support access
└── common/      # errors, money helpers, base entities, request id
```

Each feature: `Controller` → `Service` → `Repository`. DTOs at the controller
boundary; never return JPA entities from controllers.

API conventions:
- List endpoints are paged: `page` (0-based), `size` (default 20, max 100),
  returning `common.PageResponse` (never Spring's `Page` directly).
- Business records referenced by others (customers, later invoices) are
  archived (`archived_at`), not hard-deleted.
- Errors are RFC 9457 `ProblemDetail` via `GlobalExceptionHandler`.

## Non-negotiable rules

### Multi-tenancy
- Every tenant-owned table has a non-null `tenant_id` column with an index.
- Tenant filtering is **centralized** (TenantContext populated from the JWT +
  a Hibernate filter / repository base). Never hand-write `WHERE tenant_id = ?`
  as the only protection, and never skip the central mechanism.
- `tenant_id` comes **only** from the authenticated principal. Never accept it
  from a request body, path, query param, or header.
- Lookups by id must be tenant-scoped: another tenant's id returns **404**, not
  403 (don't leak existence).
- Every new tenant-owned endpoint gets an isolation test: tenant A cannot read,
  update, delete, or list tenant B's data.

How it's implemented (Hibernate partitioned multi-tenancy):
- Tenant-owned entities mark their `tenantId` field with `@TenantId`. Hibernate
  then filters every JPQL/Criteria query and `findById` by the current tenant,
  and fills `tenant_id` on insert. Don't set it by hand; don't add a
  `tenantId` parameter to repository methods.
- `TenantContextFilter` sets `TenantContext` from the JWT's `tenant_id` claim
  and clears it in `finally`. `TenantIdentifierResolver` hands it to Hibernate
  and fails closed (no context → matches nothing). There is no bypass tenant.
- Hibernate reads the tenant when the session opens (transaction start), so set
  the context *before* `@Transactional` code runs. Outside a request (scheduled
  jobs, `@Async`) wrap work in `TenantContext.callAs(tenantId, ...)`.
- Native SQL and `JdbcTemplate` bypass the filter: don't use them on
  tenant-owned tables in main code (tests may, to assert raw DB state).
- `TenantSchemaGuardTests` fails if an entity maps `tenant_id` without
  `@TenantId`, or a `tenant_id` column is nullable or unindexed.

### Platform admin
- Platform admins (`platform_admins`, no `tenant_id`) are the SaaS operator,
  not a tenant role. Their tokens carry `scope=platform` and no `tenant_id`.
- `/api/platform/**` has its own security chain that accepts only platform
  tokens; the tenant chain rejects tokens with a `scope`. Keep it that way.
- Platform endpoints work on the `tenants` table only. They never read tenant
  business data and never bypass the tenant filter.
- The only way platform code reads tenant-owned data is
  `PlatformSupportService.readAs`: active grant from the tenant required, access
  log written first, then `callAs(tenantId)` in a read-only transaction
  (PostgreSQL rejects writes). New support views must go through it.
- The first platform admin comes from `PLATFORM_ADMIN_EMAIL` /
  `PLATFORM_ADMIN_PASSWORD` at startup; there is no default account.

### Money
- Money is `BigDecimal` everywhere. Never `double`/`float`, never
  `new BigDecimal(double)` — use `BigDecimal.valueOf` or string constructors.
- DB columns: `NUMERIC(19,2)` for amounts, `NUMERIC(5,2)` for percentages.
- Rounding: `RoundingMode.HALF_UP`, scale 2, applied per line item, then summed.
  Document any deviation in the PR.
- Compare with `compareTo`, not `equals` (scale differs: `1.0` vs `1.00`).

### Invoice numbering
- Numbers are unique per `(tenant_id, year)` and gapless under concurrency.
  Use row locking or a per-tenant counter table — not `MAX(...) + 1`.
- Must be covered by a concurrent test (parallel threads, assert no duplicates).

### Database
- Schema changes only via new Flyway migrations (`V<n>__description.sql`).
  Never edit a migration that has been merged.
- `spring.jpa.hibernate.ddl-auto` stays `validate`.

### Security
- No secrets in code or committed config. Use env vars; document them in
  `.env.example`.
- Passwords hashed with BCrypt. Don't log tokens, passwords, or full request
  bodies.

### AI feature
- Claude API output is untrusted: validate against the JSON schema, retry on
  invalid output (bounded), then fail clearly.
- Extraction produces a draft response only; persisting requires an explicit
  user action through the normal invoice endpoint (same validation as manual).
- API key via env var `ANTHROPIC_API_KEY`. Tests mock the client.

## Testing

- Integration tests use Testcontainers PostgreSQL (shared container per test
  run). No H2, no mocking the database.
- Unit tests for pure logic (money math, numbering format, status transitions).
- Don't delete, skip, or `@Disabled` a failing test to make CI green — fix the
  cause or stop and explain.

## Workflow

- One feature = one branch = one PR. Branch names: `feat/<name>`,
  `fix/<name>`, `refactor/<name>`, `docs/<name>`, `ci/<name>`.
- Never commit directly to `main`.
- Commit messages: Conventional Commits (`feat: add customer CRUD`).
- Fill in the PR template, including what the agent was asked to do and what
  the human corrected.
- Changes to `auth/` and `tenant/` are reviewed line by line by a human. Agents
  must call out any change in those packages explicitly in the PR description.
- Don't add dependencies without stating why in the PR.
