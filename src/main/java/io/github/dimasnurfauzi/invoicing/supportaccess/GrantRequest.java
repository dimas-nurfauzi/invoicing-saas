package io.github.dimasnurfauzi.invoicing.supportaccess;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GrantRequest(@NotNull @Min(1) @Max(72) Integer durationHours) {}
