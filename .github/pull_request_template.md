## What

<!-- What this PR changes, in 1–3 sentences. -->

## Why

<!-- The feature/bug and why it matters. Link the issue if there is one. -->

## What I directed the agent to do

<!-- The prompts/instructions given to the AI agent and the plan it followed. -->

-

## What I corrected

<!-- What the agent got wrong or I changed after review, and why.
     "Nothing" is a valid answer — but say what you checked. -->

-

## Touches auth or multi-tenancy?

- [ ] No
- [ ] Yes — reviewed line by line; isolation tests added/updated

## How it was tested

- [ ] `./mvnw verify` passes locally
- [ ] New/updated tests:
- [ ] Manual check (curl / HTTP client):

## Checklist

- [ ] Tenant-owned queries go through the central tenant filter
- [ ] Money uses `BigDecimal` (no `double`), rounding per `CLAUDE.md`
- [ ] Schema changes are a new Flyway migration (no edits to merged ones)
- [ ] No secrets committed; new env vars added to `.env.example`
- [ ] New dependencies are justified above
