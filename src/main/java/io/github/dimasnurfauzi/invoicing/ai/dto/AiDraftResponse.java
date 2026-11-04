package io.github.dimasnurfauzi.invoicing.ai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A draft for review; nothing is saved. {@code customerId}, {@code currency}, dates, {@code notes}
 * and {@code lines} have the same shape as {@code POST /api/invoices}, so after review the client
 * sends them there (same validation as manual entry). {@code customerId} is null when no single
 * customer of the caller's tenant matched {@code customerName}. {@code previewTotal} is computed
 * with the normal invoice math.
 */
public record AiDraftResponse(
    UUID customerId,
    String customerName,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    String notes,
    List<Line> lines,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal previewTotal,
    List<String> warnings,
    int attempts) {

  /** Same fields as {@code InvoiceRequest.Line}; money and quantities as JSON strings. */
  public record Line(
      String description,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountPercent,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxRatePercent) {

    public static Line from(InvoiceRequest.Line line) {
      return new Line(
          line.description(),
          line.quantity(),
          line.unitPrice(),
          line.discountPercent(),
          line.taxRatePercent());
    }
  }
}
