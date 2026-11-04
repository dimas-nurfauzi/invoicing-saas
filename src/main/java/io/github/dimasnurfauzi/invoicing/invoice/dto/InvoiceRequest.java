package io.github.dimasnurfauzi.invoicing.invoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.dimasnurfauzi.invoicing.invoice.domain.InvoiceLineDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create and full update (PUT) of a draft. Money may be sent as a JSON string or number. No tenant
 * id, no amounts: totals are always computed. {@code customerId} is looked up in the caller's
 * tenant only.
 */
public record InvoiceRequest(
    @NotNull UUID customerId,
    @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code")
        String currency,
    @NotNull LocalDate issueDate,
    @NotNull LocalDate dueDate,
    @Size(max = 2000) String notes,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> lines) {

  @JsonIgnore
  @AssertTrue(message = "dueDate must not be before issueDate")
  public boolean isDueDateOnOrAfterIssueDate() {
    return issueDate == null || dueDate == null || !dueDate.isBefore(issueDate);
  }

  public record Line(
      @NotBlank @Size(max = 500) String description,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3)
          BigDecimal quantity,
      @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal unitPrice,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal discountPercent,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal taxRatePercent) {

    public InvoiceLineDraft toDraft() {
      return new InvoiceLineDraft(
          description.trim(),
          quantity,
          unitPrice,
          discountPercent == null ? BigDecimal.ZERO : discountPercent,
          taxRatePercent == null ? BigDecimal.ZERO : taxRatePercent);
    }
  }
}
