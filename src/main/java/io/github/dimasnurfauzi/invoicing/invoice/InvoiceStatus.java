package io.github.dimasnurfauzi.invoicing.invoice;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle and the only allowed transitions:
 *
 * <pre>
 * DRAFT   → SENT                (issue: gets a number)
 * SENT    → PAID, OVERDUE, VOID
 * OVERDUE → PAID, VOID
 * PAID, VOID: final
 * </pre>
 *
 * Drafts are deleted, not voided. PAID and OVERDUE are reached by payments and the overdue job.
 */
public enum InvoiceStatus {
  DRAFT,
  SENT,
  PAID,
  OVERDUE,
  VOID;

  public Set<InvoiceStatus> allowedNext() {
    return switch (this) {
      case DRAFT -> EnumSet.of(SENT);
      case SENT -> EnumSet.of(PAID, OVERDUE, VOID);
      case OVERDUE -> EnumSet.of(PAID, VOID);
      case PAID, VOID -> EnumSet.noneOf(InvoiceStatus.class);
    };
  }

  public boolean canTransitionTo(InvoiceStatus next) {
    return allowedNext().contains(next);
  }
}
