package io.github.dimasnurfauzi.invoicing.platform;

import io.github.dimasnurfauzi.invoicing.tenant.Tenant;
import io.github.dimasnurfauzi.invoicing.tenant.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/** What a platform admin sees about a tenant: account metadata only, no business data. */
public record PlatformTenantResponse(
    UUID id,
    String name,
    String slug,
    TenantStatus status,
    Instant createdAt,
    Instant suspendedAt) {

  static PlatformTenantResponse from(Tenant tenant) {
    return new PlatformTenantResponse(
        tenant.getId(),
        tenant.getName(),
        tenant.getSlug(),
        tenant.getStatus(),
        tenant.getCreatedAt(),
        tenant.getSuspendedAt());
  }
}
