package io.github.dimasnurfauzi.invoicing.auth;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {}
