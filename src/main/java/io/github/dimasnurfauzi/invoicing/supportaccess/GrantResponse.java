package io.github.dimasnurfauzi.invoicing.supportaccess;

import java.time.Instant;
import java.util.UUID;

public record GrantResponse(
    UUID id,
    UUID grantedBy,
    Instant createdAt,
    Instant expiresAt,
    Instant revokedAt,
    boolean active) {

  static GrantResponse from(SupportAccessGrant grant, Instant now) {
    return new GrantResponse(
        grant.getId(),
        grant.getGrantedBy(),
        grant.getCreatedAt(),
        grant.getExpiresAt(),
        grant.getRevokedAt(),
        grant.isActiveAt(now));
  }
}
