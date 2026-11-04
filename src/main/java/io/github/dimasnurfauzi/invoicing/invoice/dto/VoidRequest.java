package io.github.dimasnurfauzi.invoicing.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}
