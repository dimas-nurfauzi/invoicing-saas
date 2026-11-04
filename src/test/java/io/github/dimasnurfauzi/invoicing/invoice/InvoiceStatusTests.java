package io.github.dimasnurfauzi.invoicing.invoice;

import static io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus.DRAFT;
import static io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus.OVERDUE;
import static io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus.PAID;
import static io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus.SENT;
import static io.github.dimasnurfauzi.invoicing.invoice.InvoiceStatus.VOID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every (from, to) pair is checked against the documented lifecycle. */
class InvoiceStatusTests {

  private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED =
      Map.of(
          DRAFT, EnumSet.of(SENT),
          SENT, EnumSet.of(PAID, OVERDUE, VOID),
          OVERDUE, EnumSet.of(PAID, VOID),
          PAID, EnumSet.noneOf(InvoiceStatus.class),
          VOID, EnumSet.noneOf(InvoiceStatus.class));

  @Test
  void allTransitionsMatchTheLifecycle() {
    for (InvoiceStatus from : InvoiceStatus.values()) {
      for (InvoiceStatus to : InvoiceStatus.values()) {
        assertThat(from.canTransitionTo(to))
            .as("%s → %s", from, to)
            .isEqualTo(ALLOWED.get(from).contains(to));
      }
    }
  }

  @Test
  void noStatusTransitionsToItself() {
    for (InvoiceStatus status : InvoiceStatus.values()) {
      assertThat(status.canTransitionTo(status)).as(status.name()).isFalse();
    }
  }
}
