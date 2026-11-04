package io.github.dimasnurfauzi.invoicing.supportaccess.entity;

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
 * One read of tenant data by a platform admin. Written before the data is read; tenant-owned so the
 * tenant can audit it. Never updated or deleted by the application.
 */
@Entity
@Table(name = "support_access_logs")
public class SupportAccessLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "grant_id", nullable = false, updatable = false)
  private UUID grantId;

  @Column(name = "platform_admin_id", nullable = false, updatable = false)
  private UUID platformAdminId;

  @Column(nullable = false, length = 200, updatable = false)
  private String reason;

  @Column(nullable = false, length = 100, updatable = false)
  private String resource;

  @Column(name = "accessed_at", nullable = false, updatable = false)
  private Instant accessedAt;

  protected SupportAccessLog() {}

  public SupportAccessLog(
      UUID grantId, UUID platformAdminId, String reason, String resource, Instant accessedAt) {
    this.grantId = grantId;
    this.platformAdminId = platformAdminId;
    this.reason = reason;
    this.resource = resource;
    this.accessedAt = accessedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getGrantId() {
    return grantId;
  }

  public UUID getPlatformAdminId() {
    return platformAdminId;
  }

  public String getReason() {
    return reason;
  }

  public String getResource() {
    return resource;
  }

  public Instant getAccessedAt() {
    return accessedAt;
  }
}
