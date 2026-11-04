package io.github.dimasnurfauzi.invoicing.invoice.domain;

import io.github.dimasnurfauzi.invoicing.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Invoice money math, per CLAUDE.md: {@link BigDecimal} only, HALF_UP to scale 2, rounded per line
 * item and then summed.
 *
 * <pre>
 * subtotal = round(quantity × unitPrice)
 * discount = round(subtotal × discount% / 100)
 * tax      = round((subtotal − discount) × tax% / 100)
 * total    = subtotal − discount + tax
 * </pre>
 *
 * Invoice totals are plain sums of the rounded line amounts, so they always match what is printed
 * per line (rounding the grand total instead could differ by a few cents).
 */
public final class InvoiceCalculator {

  public static final int SCALE = 2;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  /** Largest value a NUMERIC(19,2) column holds. */
  public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

  public record LineAmounts(
      BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {}

  public record Totals(
      BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total) {}

  private InvoiceCalculator() {}

  public static LineAmounts line(
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountPercent,
      BigDecimal taxRatePercent) {
    BigDecimal subtotal = round(quantity.multiply(unitPrice));
    BigDecimal discount = round(percentOf(subtotal, discountPercent));
    BigDecimal taxable = subtotal.subtract(discount);
    BigDecimal tax = round(percentOf(taxable, taxRatePercent));
    BigDecimal total = taxable.add(tax);
    requireStorable(total);
    return new LineAmounts(subtotal, discount, tax, total);
  }

  public static Totals totals(List<LineAmounts> lines) {
    BigDecimal subtotal = zero();
    BigDecimal discount = zero();
    BigDecimal tax = zero();
    BigDecimal total = zero();
    for (LineAmounts line : lines) {
      subtotal = subtotal.add(line.subtotal());
      discount = discount.add(line.discount());
      tax = tax.add(line.tax());
      total = total.add(line.total());
    }
    requireStorable(subtotal);
    requireStorable(total);
    return new Totals(subtotal, discount, tax, total);
  }

  /** {@code amount × percent / 100}, exact (dividing by 100 only shifts the decimal point). */
  private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
    return amount.multiply(percent).movePointLeft(2);
  }

  private static BigDecimal round(BigDecimal value) {
    return value.setScale(SCALE, ROUNDING);
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(SCALE);
  }

  private static void requireStorable(BigDecimal amount) {
    if (amount.compareTo(MAX_AMOUNT) > 0) {
      throw new BadRequestException("Invoice amount is too large");
    }
  }
}
