package io.github.dimasnurfauzi.invoicing.invoice;

/** Invoice number format: {@code INV-<year>-<sequence>}, sequence zero-padded to 4 digits. */
public final class InvoiceNumbers {

  private InvoiceNumbers() {}

  public static String format(int year, int sequence) {
    if (sequence < 1) {
      throw new IllegalArgumentException("sequence must be positive");
    }
    return "INV-%d-%04d".formatted(year, sequence);
  }
}
