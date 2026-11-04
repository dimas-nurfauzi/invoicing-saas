package io.github.dimasnurfauzi.invoicing.invoice;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** List row: no lines. Money as JSON strings. */
public record InvoiceSummaryResponse(
    UUID id,
    String invoiceNumber,
    InvoiceStatus status,
    InvoiceResponse.CustomerRef customer,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountPaid,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceDue) {

  public static InvoiceSummaryResponse from(Invoice invoice) {
    return new InvoiceSummaryResponse(
        invoice.getId(),
        invoice.getInvoiceNumber(),
        invoice.getStatus(),
        new InvoiceResponse.CustomerRef(
            invoice.getCustomer().getId(), invoice.getCustomer().getName()),
        invoice.getCurrency(),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getTotal(),
        invoice.getAmountPaid(),
        invoice.getBalanceDue());
  }
}
