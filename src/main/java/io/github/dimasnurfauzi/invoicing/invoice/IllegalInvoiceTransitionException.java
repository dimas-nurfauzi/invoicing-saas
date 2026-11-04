package io.github.dimasnurfauzi.invoicing.invoice;

import io.github.dimasnurfauzi.invoicing.common.ConflictException;

/** 409: the invoice's current status doesn't allow the requested change. */
public class IllegalInvoiceTransitionException extends ConflictException {

  public IllegalInvoiceTransitionException(InvoiceStatus from, InvoiceStatus to) {
    super("Invoice can't go from %s to %s".formatted(from, to));
  }
}
