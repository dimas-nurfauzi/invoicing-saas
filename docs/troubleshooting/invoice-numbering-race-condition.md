# Race conditions: invoice numbering and payments

**Context:** invoices get legal, sequential numbers per tenant and year
(`INV-2026-0001`, `INV-2026-0002`, …). `CLAUDE.md` requires them to be **unique**
and **gapless** under concurrency, built with row locking or a counter table,
not `MAX(...) + 1`, and covered by a concurrent test. The same kind of race
turned up again, less visibly, in partial payments.

**Result:** numbers are assigned under a row lock in the same transaction that
issues the invoice; payments lock the invoice row. Both are covered by tests
that release parallel requests at the same instant, and mutation checks showed
those tests fail when the locks are removed.

---

## 1. Why the obvious solutions break

**`MAX(number) + 1`:**

```
T1: SELECT max(seq) → 41          T2: SELECT max(seq) → 41
T1: INSERT INV-…-0042             T2: INSERT INV-…-0042   ← duplicate
```

Two transactions read the same maximum before either commits. A unique index
turns the duplicate into an error, but the user still gets a failure instead of
a number.

**A database sequence (`nextval`)** is concurrency-safe but **not gapless**:
sequences are not transactional, so a rolled-back issue still burns a number.
It is also global, while numbers here restart per tenant and per year.

**Numbering drafts** would also leave gaps, because drafts can be deleted. So
drafts have no number at all; the number is assigned when the invoice is issued
(DRAFT → SENT), and a database CHECK enforces "only drafts have no number".

## 2. Design

A counter row per `(tenant, year)`:

```sql
CREATE TABLE invoice_number_counters (
    tenant_id   UUID NOT NULL,   -- tenant-owned, filtered like every other table
    number_year INT  NOT NULL,
    last_value  INT  NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, number_year)
);
```

`POST /api/invoices/{id}/issue` (`invoice/InvoiceIssueService`):

1. Read the invoice and check it can be issued (draft, customer not archived).
   Short transaction. Nothing is reserved yet.
2. Make sure the counter row for the year exists, in its own short transaction.
   If two requests create it at once, one hits the unique constraint; that's
   caught and ignored, the row exists either way.
3. **One transaction:**
   - `SELECT … FROM invoices WHERE id = ? FOR UPDATE`, then check again that it's
     still a draft (another request may have issued it meanwhile);
   - `SELECT … FROM invoice_number_counters WHERE number_year = ? FOR UPDATE`;
   - `last_value = last_value + 1`, assign `INV-<year>-<last_value>`, mark SENT.

Why this is correct:

- **No duplicates:** every issuer for the same tenant and year waits on the
  counter row lock, so increments are serialized.
- **No gaps:** the increment is part of the issuing transaction. If anything
  fails afterwards, the increment rolls back with it. Validation happens before
  a number is taken.
- **No deadlocks:** locks are always taken in the same order, invoice then
  counter.
- **Second line of defence:** a unique index on `(tenant_id, invoice_number)`.

One design choice is easy to get wrong: steps 1–3 are separate transactions in
sequence, not one big transaction that opens a `REQUIRES_NEW` transaction to
create the counter. With 10 concurrent requests, each would hold one pooled
connection while waiting for a second; with a pool of 10 connections that can
stall every request. In sequence, each request holds at most one connection.

## 3. Evidence

`InvoiceNumberingConcurrencyTests` goes through the real HTTP stack and
PostgreSQL. A `CountDownLatch` holds all requests back and releases them at the
same moment, so they really race:

| Test | Result |
|---|---|
| 20 drafts issued from 10 threads | all 200; numbers exactly `INV-2026-0001` … `0020`, each once; counter = 20 |
| the same draft issued by 10 threads | exactly 1 × 200, 9 × 409; counter = 1 (the losers never reached the counter) |

`InvoiceIssueTests` adds: a failed issue (archived customer) doesn't consume a
number; voided invoices keep their number and the sequence continues; numbers
follow the issue date's year; each tenant starts at `0001`.

### Mutation check

Removed `@Lock(PESSIMISTIC_WRITE)` from the counter query (temporarily):
`parallelIssuesGetUniqueGaplessNumbers` **failed**. Concurrent issues read the
same `last_value`, produced the same number, and the unique index rejected the
duplicates, so users got 500 errors. The unique index stopped bad data, but
only the lock gives every request a correct number.

## 4. The same race, hidden: partial payments

Payments add to `invoices.amount_paid`; the invoice becomes PAID when the
balance reaches zero; overpaying is rejected. There is also a database CHECK
`amount_paid <= total`. It seemed that the CHECK alone would stop overpayment.
It doesn't.

`PaymentConcurrencyTests`: an invoice of `111000.00`, 10 parallel payments of
`20000.00` each. Only 5 fit.

| | Accepted | `amount_paid` | Sum of payment rows |
|---|---|---|---|
| With the invoice row locked (`FOR UPDATE`) | 5 | `100000.00` | `100000.00` |
| **Mutation:** lock removed (`findById`) | **10** | not measured; never above the total (the CHECK didn't fire) | **`200000.00`** (10 rows × 20000) |

Without the lock, requests read the same stale `amount_paid`, each checked
`stale + 20000 <= 111000` (true), and wrote back `stale + 20000`, overwriting
each other (a **lost update**). So `amount_paid` stayed within the total and the
CHECK never fired, while ten payment rows totalling 200,000 were recorded
against a 111,000 invoice. The constraint catches a single bad write; it can't
see a race between correct-looking writes. Only serializing on the invoice row
fixes it.

The same rule then shaped the overdue job: it selects SENT invoices past their
due date `FOR UPDATE`. If a payment commits first, PostgreSQL (READ COMMITTED)
re-checks the WHERE clause after the lock wait, the invoice is now PAID and
drops out instead of being overwritten as OVERDUE.

`CLAUDE.md` now says: anything that changes an invoice's status or amounts locks
the invoice row first.

## 5. Lessons

1. "Unique" and "gapless" are different requirements: sequences give the first,
   transactions plus a locked counter give both.
2. Constraints are a safety net for bad values, not for races between good
   ones. Lost updates pass every constraint.
3. Lock in a fixed order, keep transactions short, and watch the connection
   pool when one request might need two connections.
4. Test concurrency for real: parallel threads, a latch, real HTTP and a real
   database. Then remove the lock and watch the test fail.

## Working with the AI agent

Built with an AI coding agent (Claude Code): `feat/invoice-issue` and
`feat/payment` (GitHub PRs #9, #10), `feat/overdue-summary` (#11).

- I directed: gapless numbering per `CLAUDE.md`, partial payments, the overdue
  job; I had the agent run the tests and open the PRs directly for this batch.
- The agent designed the counter table + lock order, split the issue flow into
  short transactions to avoid pool exhaustion, wrote the latch-based tests and
  ran the mutation checks, which is how the payment lost update was found.

What I corrected / decided:

- <!-- fill in -->
