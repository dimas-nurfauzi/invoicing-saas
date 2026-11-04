package io.github.dimasnurfauzi.invoicing.supportaccess.dto;

import io.github.dimasnurfauzi.invoicing.supportaccess.entity.SupportAccessGrant;
import java.time.Instant;
import java.util.UUID;

public record GrantResponse(
    UUID id,
    UUID grantedBy,
    Instant createdAt,
    Instant expiresAt,
    Instant revokedAt,
    boolean active) {

  public static GrantResponse from(SupportAccessGrant grant, Instant now) {
    return new GrantResponse(
        grant.getId(),
        grant.getGrantedBy(),
        grant.getCreatedAt(),
        grant.getExpiresAt(),
        grant.getRevokedAt(),
        grant.isActiveAt(now));
  }
}
