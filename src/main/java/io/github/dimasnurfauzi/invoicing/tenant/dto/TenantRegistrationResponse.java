package io.github.dimasnurfauzi.invoicing.tenant.dto;

import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import java.util.UUID;

public record TenantRegistrationResponse(
    UUID tenantId, String tenantSlug, UUID userId, String email, Role role) {}
