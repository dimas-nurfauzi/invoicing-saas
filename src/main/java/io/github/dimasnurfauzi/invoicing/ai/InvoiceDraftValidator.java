package io.github.dimasnurfauzi.invoicing.ai;

import io.github.dimasnurfauzi.invoicing.invoice.InvoiceRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Value rules for a schema-valid draft. Lines are checked with the same Bean Validation constraints
 * as a manually entered invoice ({@link InvoiceRequest.Line}), so an accepted draft can be posted
 * to {@code POST /api/invoices} as-is.
 */
@Component
public class InvoiceDraftValidator {

  static final int MAX_LINES = 100;

  private final Validator validator;

  public InvoiceDraftValidator(Validator validator) {
    this.validator = validator;
  }

  /** The draft converted to request lines, or the problems found. */
  public record Result(List<InvoiceRequest.Line> lines, List<String> errors) {
    boolean valid() {
      return errors.isEmpty();
    }
  }

  public Result validate(ExtractedDraft draft) {
    List<String> errors = new ArrayList<>();
    if (draft.lines().isEmpty()) {
      errors.add("lines: at least one line item is required");
    }
    if (draft.lines().size() > MAX_LINES) {
      errors.add("lines: at most " + MAX_LINES + " line items");
    }
    if (!draft.currency().isBlank() && !draft.currency().matches("^[A-Z]{3}$")) {
      errors.add("currency: must be a 3-letter upper-case ISO code or empty");
    }
    LocalDate issue = date("issueDate", draft.issueDate(), errors);
    LocalDate due = date("dueDate", draft.dueDate(), errors);
    if (issue != null && due != null && due.isBefore(issue)) {
      errors.add("dueDate: must not be before issueDate");
    }

    List<InvoiceRequest.Line> lines = new ArrayList<>();
    for (int i = 0; i < draft.lines().size(); i++) {
      ExtractedDraft.Line line = draft.lines().get(i);
      String at = "lines[" + i + "]";
      InvoiceRequest.Line request =
          new InvoiceRequest.Line(
              line.description(),
              decimal(at + ".quantity", line.quantity(), errors),
              decimal(at + ".unitPrice", line.unitPrice(), errors),
              percent(at + ".discountPercent", line.discountPercent(), errors),
              percent(at + ".taxRatePercent", line.taxRatePercent(), errors));
      validator.validate(request).stream()
          .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
          .map(v -> describe(at, v))
          .forEach(errors::add);
      lines.add(request);
    }
    return new Result(lines, errors);
  }

  private static String describe(String at, ConstraintViolation<InvoiceRequest.Line> v) {
    return at + "." + v.getPropertyPath() + ": " + v.getMessage();
  }

  private static LocalDate date(String field, String value, List<String> errors) {
    if (value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      errors.add(field + ": must be YYYY-MM-DD or empty");
      return null;
    }
  }

  /** Plain decimal with a dot; anything else (commas, "1,5jt") is an error for the retry. */
  private static BigDecimal decimal(String field, String value, List<String> errors) {
    if (value.isBlank()) {
      errors.add(field + ": is required");
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      errors.add(field + ": must be a plain decimal number with a dot, e.g. 1500000 or 1.5");
      return null;
    }
  }

  /** Empty means 0 for percentages. */
  private static BigDecimal percent(String field, String value, List<String> errors) {
    return value.isBlank() ? BigDecimal.ZERO : decimal(field, value, errors);
  }
}
