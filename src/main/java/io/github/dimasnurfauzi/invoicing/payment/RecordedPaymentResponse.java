package io.github.dimasnurfauzi.invoicing.payment;

import io.github.dimasnurfauzi.invoicing.invoice.InvoiceResponse;

/** The new payment plus the invoice after it (status, amountPaid, balanceDue). */
public record RecordedPaymentResponse(PaymentResponse payment, InvoiceResponse invoice) {}
