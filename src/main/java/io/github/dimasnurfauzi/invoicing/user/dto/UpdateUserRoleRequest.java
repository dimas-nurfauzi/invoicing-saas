package io.github.dimasnurfauzi.invoicing.user.dto;

import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {}
