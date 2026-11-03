package io.github.dimasnurfauzi.invoicing.tenant;

import io.github.dimasnurfauzi.invoicing.auth.Role;
import java.util.UUID;

public record TenantRegistrationResponse(
    UUID tenantId, String tenantSlug, UUID userId, String email, Role role) {}
