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

## Roadmap

- [x] Step 0 — repo, agent guide (`CLAUDE.md`), PR template
- [ ] Step 1 — skeleton, Docker Compose, health endpoint, Testcontainers, CI
- [ ] Week 1 — tenants, users, JWT auth, centralized tenant filter, isolation tests
- [ ] Week 2 — customers, invoices, line items, tax, discount
- [ ] Week 3 — partial payments, invoice numbering, overdue job
- [ ] Week 4 — AI draft extraction, troubleshooting write-ups

## Troubleshooting write-ups

Planned:
- Race condition in invoice numbering
- Cross-tenant data leak
- Money precision and tax rounding
