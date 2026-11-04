package io.github.dimasnurfauzi.invoicing.summary;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Billing summary of the caller's tenant, one entry per currency (amounts in different currencies
 * are never added together). Money as JSON strings.
 *
 * <ul>
 *   <li>billed: totals of SENT, OVERDUE and PAID invoices (drafts and voided ones excluded)
 *   <li>paid: payments received on those invoices
 *   <li>outstanding: billed − paid, i.e. still to collect on SENT and OVERDUE
 *   <li>overdue: the part of outstanding that is OVERDUE
 * </ul>
 */
public record SummaryResponse(LocalDate from, LocalDate to, List<CurrencySummary> currencies) {

  public record CurrencySummary(
      String currency,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalBilled,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPaid,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalOutstanding,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalOverdue,
      Map<InvoiceStatus, Long> invoiceCounts) {}
}
