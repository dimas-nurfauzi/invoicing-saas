package io.github.dimasnurfauzi.invoicing.invoice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimasnurfauzi.invoicing.common.exception.BadRequestException;
import io.github.dimasnurfauzi.invoicing.invoice.domain.InvoiceCalculator.LineAmounts;
import io.github.dimasnurfauzi.invoicing.invoice.domain.InvoiceCalculator.Totals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Money math. Assertions use {@code isEqualTo} with exact-scale values on purpose: BigDecimal
 * equals() also checks the scale, so these prove every amount is scale 2.
 */
class InvoiceCalculatorTests {

  private static BigDecimal d(String value) {
    return new BigDecimal(value);
  }

  private static LineAmounts line(String qty, String price, String discount, String tax) {
    return InvoiceCalculator.line(d(qty), d(price), d(discount), d(tax));
  }

  @Test
  void simpleLineWithTax() {
    LineAmounts amounts = line("2", "50000.00", "0", "11");

    assertThat(amounts.subtotal()).isEqualTo(d("100000.00"));
    assertThat(amounts.discount()).isEqualTo(d("0.00"));
    assertThat(amounts.tax()).isEqualTo(d("11000.00"));
    assertThat(amounts.total()).isEqualTo(d("111000.00"));
  }

  @Test
  void discountIsAppliedBeforeTax() {
    LineAmounts amounts = line("1", "100.00", "10", "11");

    assertThat(amounts.discount()).isEqualTo(d("10.00"));
    assertThat(amounts.tax()).isEqualTo(d("9.90")); // 11% of 90.00, not of 100.00
    assertThat(amounts.total()).isEqualTo(d("99.90"));
  }

  @Test
  void roundsHalfUpAtTheBoundary() {
    assertThat(line("1.005", "1.00", "0", "0").subtotal()).isEqualTo(d("1.01"));
    assertThat(line("1.004", "1.00", "0", "0").subtotal()).isEqualTo(d("1.00"));
    // 1% of 0.50 = 0.005 → 0.01
    assertThat(line("1", "0.50", "0", "1").tax()).isEqualTo(d("0.01"));
    // 0.333 × 10.00 = 3.33; 11% of 3.33 = 0.3663 → 0.37
    LineAmounts fractional = line("0.333", "10.00", "0", "11");
    assertThat(fractional.subtotal()).isEqualTo(d("3.33"));
    assertThat(fractional.tax()).isEqualTo(d("0.37"));
  }

  @Test
  void totalsAreSumsOfRoundedLinesNotRoundedSums() {
    // Each line: 11% of 33.33 = 3.6663 → 3.67. Three lines → 11.01.
    // Rounding the grand total instead would give 11% of 99.99 = 10.9989 → 11.00.
    LineAmounts each = line("1", "33.33", "0", "11");
    Totals totals = InvoiceCalculator.totals(List.of(each, each, each));

    assertThat(totals.subtotal()).isEqualTo(d("99.99"));
    assertThat(totals.taxTotal()).isEqualTo(d("11.01"));
    assertThat(totals.total()).isEqualTo(d("111.00"));
    assertThat(totals.total())
        .isEqualTo(totals.subtotal().subtract(totals.discountTotal()).add(totals.taxTotal()));
  }

  @Test
  void fullDiscountGivesZero() {
    LineAmounts amounts = line("3", "250000.00", "100", "11");

    assertThat(amounts.discount()).isEqualTo(d("750000.00"));
    assertThat(amounts.tax()).isEqualTo(d("0.00"));
    assertThat(amounts.total()).isEqualTo(d("0.00"));
  }

  @Test
  void emptyTotalsAreZeroAtScale2() {
    Totals totals = InvoiceCalculator.totals(List.of());

    assertThat(totals.total()).isEqualTo(d("0.00"));
  }

  @Test
  void compareWithCompareToNotEquals() {
    // Why CLAUDE.md says compareTo: equal amounts with different scales are not equals().
    assertThat(d("1.0")).isNotEqualTo(d("1.00"));
    assertThat(d("1.0")).isEqualByComparingTo(d("1.00"));
  }

  @Test
  void amountsBeyondNumeric19_2AreRejected() {
    assertThatThrownBy(() -> line("999999999", "999999999999999.99", "0", "0"))
        .isInstanceOf(BadRequestException.class);
  }
}
