package io.github.dimasnurfauzi.invoicing.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InvoiceNumbersTests {

  @Test
  void zeroPadsToFourDigits() {
    assertThat(InvoiceNumbers.format(2026, 1)).isEqualTo("INV-2026-0001");
    assertThat(InvoiceNumbers.format(2026, 42)).isEqualTo("INV-2026-0042");
    assertThat(InvoiceNumbers.format(2026, 9999)).isEqualTo("INV-2026-9999");
  }

  @Test
  void growsBeyondFourDigitsInsteadOfWrapping() {
    assertThat(InvoiceNumbers.format(2026, 10000)).isEqualTo("INV-2026-10000");
  }

  @Test
  void rejectsNonPositiveSequence() {
    assertThatThrownBy(() -> InvoiceNumbers.format(2026, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
