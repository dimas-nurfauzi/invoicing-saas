# Preventing a cross-tenant data leak

**Context:** multi-tenant invoicing SaaS. Every business (tenant) shares the same
tables; a `tenant_id` column says whose row it is. A leak means tenant A seeing,
changing or even learning about the existence of tenant B's data.

**Result:** tenant isolation is enforced in one place (Hibernate `@TenantId`),
fails closed, has no bypass, and is proven by isolation tests that were shown
to fail when the protection is removed.

---

## 1. How these leaks usually happen

None of them need an attacker to be clever. They need one developer to forget
one thing once:

| Mistake | Example |
|---|---|
| A query without the tenant condition | `SELECT * FROM invoices WHERE id = ?` |
| Lookup by id | `repository.findById(id)` with an id from the URL |
| Trusting the client for the tenant | `tenantId` in the body, path, query or a header |
| A raw SQL / `JdbcTemplate` shortcut | a report query that skips the ORM |
| A background job | no HTTP request, so no tenant at all, so "all tenants" |
| Leaking existence | answering `403 Forbidden` for another tenant's id (it exists!) |
| A tenant left on a pooled thread | a `ThreadLocal` not cleared, reused by the next request |

Hand-writing `WHERE tenant_id = ?` in every query is exactly the kind of rule
that holds until the day it doesn't. So the rule in `CLAUDE.md` is: **filtering
is centralized, and `tenant_id` comes only from the authenticated principal.**

## 2. Options considered

| | Hibernate `@TenantId` | Hibernate `@Filter` | PostgreSQL row-level security |
|---|---|---|---|
| JPQL / Criteria queries | filtered automatically | only if the filter is enabled on that session | filtered in the DB |
| `findById` / `em.find` | **filtered** (verified, see §4) | **not** filtered (documented limitation) | filtered |
| Insert | `tenant_id` filled in by Hibernate | manual | checked by the DB |
| Can be forgotten? | only the annotation (a guard test catches that) | yes, per session | no |
| Cost | low | medium | high (`SET LOCAL` per transaction with a connection pool) |

**Chosen: `@TenantId`** (Hibernate partitioned multi-tenancy). Nothing has to be
"turned on" per request, inserts are covered too, and it's a few lines of code.
RLS stays a possible defence-in-depth layer later.

## 3. Design

```
Request ── Bearer JWT ──▶ BearerTokenAuthenticationFilter (signature, exp, issuer, claims)
                          │
                          ▼
                    TenantContextFilter ── tenant_id claim ──▶ TenantContext (ThreadLocal)
                          │                                     cleared in finally
                          ▼
            Controller → Service → Repository
                                        │  session opens
                                        ▼
                    TenantIdentifierResolver ──▶ Hibernate adds "tenant_id = ?" to every
                                                 query on @TenantId entities, sets it on insert
```

- **Source of the tenant:** only the `tenant_id` claim of a validated JWT
  (`tenant/context/TenantContextFilter`). Request bodies with `tenantId` are ignored;
  there is no tenant parameter on any repository method.
- **Fail closed:** with no tenant in context, `TenantIdentifierResolver` returns
  `00000000-0000-0000-0000-000000000000`, which matches no row. Reads come back
  empty and inserts violate the foreign key. There is no "root" tenant.
- **404, not 403:** another tenant's row is simply not found by the filtered
  query, so the API answers the same `404` as for an id that never existed.
- **Context hygiene:** the filter clears `TenantContext` at the start and in
  `finally`, because Tomcat reuses threads.
- **No request, no tenant:** login and registration resolve the tenant
  themselves and run their user queries inside `TenantContext.callAs(tenantId, …)`;
  the overdue job loops over tenants and does the same. Even platform support
  access to tenant data goes through `callAs` (with the tenant's permission and
  an audit log), never around the filter.
- **Guard rails:** `TenantSchemaGuardTests` fails the build if an entity maps
  `tenant_id` without `@TenantId`, or a `tenant_id` column is nullable or
  unindexed. New tables can't silently skip the rules.

One subtlety cost some thought: Hibernate reads the tenant **when the session
opens**, i.e. at the start of the transaction. Code that sets the tenant inside
a `@Transactional` method is too late. That's why login and registration are not
`@Transactional`, and wrap `callAs` around a `TransactionTemplate` instead.

## 4. Evidence

`TenantIsolationTests` (and the same pattern for customers, invoices and
payments) registers two real tenants over HTTP, then with tenant A's valid
ADMIN token tries to read, list, update and delete tenant B's data:

- list returns only A's rows;
- `GET/PATCH/DELETE` on B's id returns **404 with a body identical** to the 404
  for a random id (only the `instance` path differs);
- B's rows are then checked **with JDBC, bypassing the filter**, so the
  assertion is about the database, not about what the filtered API shows;
- `tenantId` / `tenant_id` smuggled into a request body is ignored;
- for invoices: A can't attach B's customer to an invoice (404, nothing created).

`TenantFilterFailsClosedTests` checks the repository layer directly: with no
tenant in context `findAll`, `count`, `findById` and `findByEmail` see nothing,
and an insert fails.

**Was `findById` really covered?** That was the open risk when choosing
`@TenantId`: `@Filter` is known not to apply to `em.find`. The test
`findByIdIsScopedToTheCurrentTenant` showed that on Hibernate 6.6 `@TenantId`
does scope `findById`, so no custom repository base was needed.

### Mutation check: do the tests catch a leak?

Green tests prove nothing if they would also be green with the bug. So the bug
was put in on purpose (temporarily):

| Mutation | Result |
|---|---|
| `UserService` reads users through native SQL (`SELECT * FROM users WHERE id = :id`), bypassing the filter | **7 / 7** `TenantIsolationTests` failed |
| `TenantContext.clear()` removed from the filter's `finally` | **2 / 3** `TenantContextFilterTests` failed |

Both were reverted; the tests catch the leaks they claim to.

## 5. What this does not protect against

- **Direct database access.** The filter protects the application path. Anyone
  with SQL access to production sees everything; limit and audit that access.
- **Native SQL and `JdbcTemplate`** bypass the filter. `CLAUDE.md` forbids them on
  tenant-owned tables in main code (tests use JDBC on purpose, to look *under*
  the filter).
- **Other threads.** `ThreadLocal` doesn't follow `@Async` or executors; such
  code must use `callAs` explicitly.

## 6. Lessons

1. Make the safe path the only path: one mechanism, no tenant parameters, no
   bypass mode. Exceptions (login, jobs, support) go *through* it via `callAs`.
2. Fail closed: no tenant should mean no data, not all data.
3. Test from the outside with two real tenants, assert on the database with the
   filter bypassed, and compare 404 bodies byte for byte.
4. Prove the tests can fail (mutation check), and add a guard test so the next
   table can't forget the rules.

## Working with the AI agent

This was built with an AI coding agent (Claude Code), one PR per step, each
starting from a plan I approved:

- `feat/tenant-registration`, `feat/jwt-auth`, `feat/tenant-filter`
  (GitHub PRs #2–#4); later reused by customers, invoices and payments.
- I directed: tenant only from the JWT, centralized filter, 404 for other
  tenants, an isolation test for every tenant-owned endpoint.
- The agent proposed `@TenantId` over `@Filter`/RLS with the comparison above,
  flagged the `findById` risk up front and proved it with a test, and ran the
  mutation checks.

What I corrected / decided:

- <!-- fill in -->
