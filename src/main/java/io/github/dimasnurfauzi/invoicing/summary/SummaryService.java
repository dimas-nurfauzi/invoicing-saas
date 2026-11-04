package io.github.dimasnurfauzi.invoicing.summary;

import io.github.dimasnurfauzi.invoicing.common.BadRequestException;
import io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Billing summary of the caller's tenant (tenant-filtered aggregate query). */
@Service
public class SummaryService {

  /** Bounds used when no date filter is given (valid PostgreSQL dates). */
  static final LocalDate EARLIEST = LocalDate.of(1, 1, 1);

  static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

  private static final Set<InvoiceStatus> BILLED =
      EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.OVERDUE, InvoiceStatus.PAID);
  private static final Set<InvoiceStatus> OPEN =
      EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.OVERDUE);

  private final SummaryRepository summaries;

  public SummaryService(SummaryRepository summaries) {
    this.summaries = summaries;
  }

  @Transactional(readOnly = true)
  public SummaryResponse summary(LocalDate from, LocalDate to) {
    LocalDate start = from == null ? EARLIEST : from;
    LocalDate end = to == null ? LATEST : to;
    if (start.isAfter(end)) {
      throw new BadRequestException("from must not be after to");
    }
    return new SummaryResponse(
        from, to, aggregate(summaries.totalsByCurrencyAndStatus(start, end)));
  }

  /** Pure aggregation, kept separate so it can be unit-tested. */
  static List<SummaryResponse.CurrencySummary> aggregate(List<StatusTotals> rows) {
    Map<String, List<StatusTotals>> byCurrency = new LinkedHashMap<>();
    for (StatusTotals row : rows) {
      byCurrency.computeIfAbsent(row.currency(), c -> new ArrayList<>()).add(row);
    }
    List<SummaryResponse.CurrencySummary> result = new ArrayList<>();
    byCurrency.forEach((currency, currencyRows) -> {
      BigDecimal billed = zero();
      BigDecimal paid = zero();
      BigDecimal outstanding = zero();
      BigDecimal overdue = zero();
      Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
      for (InvoiceStatus status : InvoiceStatus.values()) {
        counts.put(status, 0L);
      }
      for (StatusTotals row : currencyRows) {
        counts.put(row.status(), row.count());
        if (BILLED.contains(row.status())) {
          billed = billed.add(row.total());
          paid = paid.add(row.paid());
        }
        if (OPEN.contains(row.status())) {
          outstanding = outstanding.add(row.total().subtract(row.paid()));
        }
        if (row.status() == InvoiceStatus.OVERDUE) {
          overdue = overdue.add(row.total().subtract(row.paid()));
        }
      }
      result.add(
          new SummaryResponse.CurrencySummary(
              currency, billed, paid, outstanding, overdue, counts));
    });
    return result;
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(2);
  }
}
