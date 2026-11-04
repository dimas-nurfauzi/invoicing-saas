package io.github.dimasnurfauzi.invoicing.invoice;

import java.math.BigDecimal;

/** The editable part of a line item; amounts are always computed, never accepted from input. */
public record InvoiceLineDraft(
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountPercent,
    BigDecimal taxRatePercent) {}
