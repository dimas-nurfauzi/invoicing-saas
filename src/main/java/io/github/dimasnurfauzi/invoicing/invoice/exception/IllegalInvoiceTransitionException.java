package io.github.dimasnurfauzi.invoicing.invoice.exception;

import io.github.dimasnurfauzi.invoicing.common.exception.ConflictException;
import io.github.dimasnurfauzi.invoicing.invoice.entity.InvoiceStatus;

/** 409: the invoice's current status doesn't allow the requested change. */
public class IllegalInvoiceTransitionException extends ConflictException {

  public IllegalInvoiceTransitionException(InvoiceStatus from, InvoiceStatus to) {
    super("Invoice can't go from %s to %s".formatted(from, to));
  }
}
