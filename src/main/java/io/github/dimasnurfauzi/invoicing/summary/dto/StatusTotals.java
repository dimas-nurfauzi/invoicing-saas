package io.github.dimasnurfauzi.invoicing.summary.dto;

import io.github.dimasnurfauzi.invoicing.invoice.entity.InvoiceStatus;
import java.math.BigDecimal;

/** One aggregate row: invoices of one currency and status. */
public record StatusTotals(
    String currency, InvoiceStatus status, Long count, BigDecimal total, BigDecimal paid) {}
