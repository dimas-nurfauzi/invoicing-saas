package io.github.dimasnurfauzi.invoicing.supportaccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * A tenant's permission for the SaaS operator to read its data until {@code expiresAt}, unless
 * revoked earlier. Tenant-owned: created, listed and revoked only within the tenant.
 */
@Entity
@Table(name = "support_access_grants")
public class SupportAccessGrant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "granted_by", nullable = false, updatable = false)
  private UUID grantedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected SupportAccessGrant() {}

  public SupportAccessGrant(UUID grantedBy, Instant createdAt, Instant expiresAt) {
    this.grantedBy = grantedBy;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public boolean isActiveAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  /** Idempotent: revoking twice keeps the first timestamp. */
  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getGrantedBy() {
    return grantedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
