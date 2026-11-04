package io.github.dimasnurfauzi.invoicing.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiDraftRequest(@NotBlank @Size(max = 5000) String text) {

  @Override
  public String toString() {
    // Free text may contain personal data: never log it.
    return "AiDraftRequest[text=<%d chars>]".formatted(text == null ? 0 : text.length());
  }
}
