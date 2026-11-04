# Money precision and tax rounding

**Context:** invoices with line items, per-line discount and tax (e.g. Indonesian
PPN 11%), partial payments and a billing summary. Amounts are shown to
customers and must add up exactly, every time, on the server, in the database
and in the browser.

**Result:** `BigDecimal` end to end, one calculator with one rounding rule,
amounts stored as computed snapshots, and money sent to clients as strings.
Unit tests pin the edge cases with exact-scale assertions.

---

## 1. Where money goes wrong

| Trap | What happens |
|---|---|
| `double` / `float` | `0.1 + 0.2 = 0.30000000000000004`; totals drift by a cent |
| `new BigDecimal(0.1)` | `0.1000000000000000055511151231257827…` (the double's exact binary value) |
| `equals` on `BigDecimal` | `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is **false** (scale differs) |
| Rounding the grand total instead of each line | printed lines don't add up to the printed total |
| Discount and tax in the wrong order | tax charged on money the customer doesn't pay |
| JSON numbers in JavaScript | IEEE-754 doubles: integers above 2^53 and many decimals aren't exact |
| Overflow | a product that doesn't fit the column → a database error (500) instead of a clear 400 |

## 2. Rules (from `CLAUDE.md`)

- `BigDecimal` everywhere; never `double`, never `new BigDecimal(double)`.
- Columns: `NUMERIC(19,2)` for amounts, `NUMERIC(5,2)` for percentages.
- `RoundingMode.HALF_UP`, scale 2, **applied per line item, then summed**.
- Compare with `compareTo`, not `equals`.

## 3. Design

All money math lives in one pure class, `invoice/domain/InvoiceCalculator`:

```
subtotal = round(quantity × unitPrice)
discount = round(subtotal × discount% / 100)
tax      = round((subtotal − discount) × tax% / 100)    ← tax on what's actually charged
total    = subtotal − discount + tax

invoice subtotal / discount / tax / total = sums of the rounded line amounts
```

- `/ 100` is `movePointLeft(2)`: exact, no division rounding to choose.
- Each line's amounts are computed when the line is created and **stored**, so
  the database, the API and a later reload all show the same numbers, and the
  stored invoice total always equals the sum of its stored lines.
- Totals are recomputed from the lines on every change; amounts in a request
  (e.g. a client sending `"total": "1"`) are ignored.
- Anything above `NUMERIC(19,2)`'s maximum is rejected with **400** before it
  reaches the database.
- Inputs are validated to at most 2 decimals for prices and percentages
  (`@Digits`). Values are normalized to the column scale with
  `RoundingMode.UNNECESSARY`, which throws if rounding would be needed, so it
  can't silently round.
- **Money in JSON is a string** (`"2164500.00"`) in every response, so a
  JavaScript client never sees a float. Requests accept strings or numbers.
- Payments compare with `compareTo`: the invoice becomes PAID when
  `balanceDue.compareTo(ZERO) == 0`.
- The summary never adds different currencies: totals are per currency.

### One documented deviation

`quantity` is `NUMERIC(19,3)`, not `(19,2)`: services and goods are billed in
fractional units (1.5 kg, 2.25 hours). `CLAUDE.md` only fixes amounts and
percentages, and every *amount* is still rounded to 2 decimals. The deviation is
recorded in the PR (#8) as `CLAUDE.md` asks.

## 4. Why per-line rounding matters: a worked example

Three lines of 1 × 33.33 with 11% tax.

| | Per line, then summed (implemented) | Rounding the total |
|---|---|---|
| Tax per line | 33.33 × 11% = 3.6663 → **3.67** | — |
| Tax total | 3 × 3.67 = **11.01** | 99.99 × 11% = 10.9989 → **11.00** |
| Invoice total | **111.00** | 110.99 |

Both are "correct" rounding, but only per-line rounding gives an invoice whose
printed lines add up to the printed total. `InvoiceCalculatorTests#totalsAreSumsOfRoundedLinesNotRoundedSums`
pins exactly this case.

## 5. Evidence

`InvoiceCalculatorTests` (pure unit tests). Assertions use `isEqualTo` with
exact-scale values (`new BigDecimal("11.01")`) on purpose: `BigDecimal.equals`
also compares the scale, so these prove every result is scale 2.

| Case | Expected |
|---|---|
| 2 × 50000.00, 11% tax | subtotal `100000.00`, tax `11000.00`, total `111000.00` |
| 100.00, 10% discount, 11% tax | discount `10.00`, tax `9.90` (11% of 90.00), total `99.90` |
| HALF_UP at the boundary | 1.005 × 1.00 → `1.01`; 1.004 × 1.00 → `1.00`; 1% of 0.50 → `0.01` |
| Fractional quantity | 0.333 × 10.00 → `3.33`; 11% → `0.37` |
| Per-line vs total rounding | tax `11.01`, total `111.00` (table above) |
| 100% discount | total `0.00`, tax `0.00` |
| No lines | `0.00` (scale 2) |
| `compareTo` vs `equals` | `1.0` vs `1.00`: not equal, but compare to 0 |
| Overflow | 999999999 × 999999999999999.99 → 400 |

`InvoiceDraftTests` checks the same math through the API and the database: a
two-line invoice (1 × 1,500,000 and 2.5 × 200,000 with 10% discount, both 11%
tax) is `2164500.00` in the response, after a reload, and in the `invoices.total`
column; `quantity` comes back as `"2.500"` before and after the reload.

## 6. The AI feature: the same rules at the edge

AI drafts (`ai/`) turn text like "desain logo 1,5jt, PPN 11%" into invoice
lines. Model output is untrusted, so numbers come back as **strings** in the
structured output, are parsed with `new BigDecimal(String)`, rejected if they
aren't plain decimals ("1,5jt" → error → retry), and then checked with the
**same** `@Digits` / range constraints as manual entry. The draft's preview
total uses `InvoiceCalculator`, so it equals the total the invoice will have
when saved (tested).

## 7. Lessons

1. One calculator, one rounding rule, stored snapshots: the number you print is
   the number you store is the number you charge.
2. Decide the rounding point (per line) explicitly and test the case where it
   changes the result.
3. Strings at the boundaries (JSON, LLM output), `BigDecimal` inside,
   `NUMERIC` in the database; never a float in between.
4. Use `UNNECESSARY` where rounding must never happen, so a wrong assumption
   throws instead of silently changing an amount.

## Working with the AI agent

Built with an AI coding agent (Claude Code): `feat/invoice-draft` (GitHub PR #8),
payments in #10, the summary in #11, AI drafts in #12.

- I directed: invoices with line items, tax and discount under the `CLAUDE.md`
  money rules; approved per-line discount/tax and money as JSON strings.
- The agent wrote the calculator and its edge-case tests, spotted that
  `"2.5"` would come back as `"2.500"` only after a reload and normalized the
  scale, and documented the `quantity` deviation.

What I corrected / decided:

- <!-- fill in -->
