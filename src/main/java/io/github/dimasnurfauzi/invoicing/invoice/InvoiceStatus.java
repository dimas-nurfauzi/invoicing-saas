package io.github.dimasnurfauzi.invoicing.invoice;

/**
 * Lifecycle: DRAFT → SENT → PAID, OVERDUE set by a scheduled job, VOID for cancelled sent invoices.
 * Only DRAFT is reachable so far; the transitions come with issuing and payments.
 */
public enum InvoiceStatus {
  DRAFT,
  SENT,
  PAID,
  OVERDUE,
  VOID
}
