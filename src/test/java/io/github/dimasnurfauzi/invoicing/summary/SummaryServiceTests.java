package io.github.dimasnurfauzi.invoicing.summary;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryServiceTests {

  private static StatusTotals row(
      String currency, InvoiceStatus status, long count, String total, String paid) {
    return new StatusTotals(currency, status, count, new BigDecimal(total), new BigDecimal(paid));
  }

  @Test
  void aggregatesPerCurrency() {
    List<SummaryResponse.CurrencySummary> result =
        SummaryService.aggregate(
            List.of(
                row("IDR", InvoiceStatus.SENT, 2, "1300.00", "400.00"),
                row("IDR", InvoiceStatus.OVERDUE, 1, "500.00", "100.00"),
                row("IDR", InvoiceStatus.PAID, 1, "2000.00", "2000.00"),
                row("IDR", InvoiceStatus.DRAFT, 3, "7777.00", "0.00"),
                row("IDR", InvoiceStatus.VOID, 1, "9999.00", "0.00"),
                row("USD", InvoiceStatus.SENT, 1, "50.00", "0.00")));

    SummaryResponse.CurrencySummary idr = result.get(0);
    assertThat(idr.currency()).isEqualTo("IDR");
    assertThat(idr.totalBilled()).isEqualTo(new BigDecimal("3800.00"));
    assertThat(idr.totalPaid()).isEqualTo(new BigDecimal("2500.00"));
    assertThat(idr.totalOutstanding()).isEqualTo(new BigDecimal("1300.00"));
    assertThat(idr.totalOverdue()).isEqualTo(new BigDecimal("400.00"));
    assertThat(idr.invoiceCounts()).containsEntry(InvoiceStatus.DRAFT, 3L);
    assertThat(result.get(1).totalBilled()).isEqualTo(new BigDecimal("50.00"));
  }

  @Test
  void missingStatusesCountAsZero() {
    SummaryResponse.CurrencySummary only =
        SummaryService.aggregate(List.of(row("IDR", InvoiceStatus.SENT, 1, "10.00", "0.00")))
            .get(0);

    assertThat(only.invoiceCounts())
        .containsEntry(InvoiceStatus.PAID, 0L)
        .containsEntry(InvoiceStatus.VOID, 0L);
    assertThat(only.totalOverdue()).isEqualTo(new BigDecimal("0.00"));
  }
}
