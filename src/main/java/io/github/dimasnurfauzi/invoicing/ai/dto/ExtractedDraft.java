package io.github.dimasnurfauzi.invoicing.ai.dto;

import java.util.List;

/** Claude's output, after it passed the JSON schema: every field present, all strings. */
public record ExtractedDraft(
    String customerName,
    String issueDate,
    String dueDate,
    String currency,
    String notes,
    List<Line> lines) {

  public record Line(
      String description,
      String quantity,
      String unitPrice,
      String discountPercent,
      String taxRatePercent) {}
}
