package io.github.dimasnurfauzi.invoicing.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * A business using the SaaS. The root of tenancy: this table itself is not tenant-owned.
 *
 * <p>The id is assigned in Java (not generated) because registration must put the new tenant in
 * {@link TenantContext} before the transaction that inserts it and its first user starts. {@link
 * Persistable} keeps Spring Data from doing a merge (select + insert) for an assigned id.
 */
@Entity
@Table(name = "tenants")
public class Tenant implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  /** Public, unique identifier used at login (email is only unique within a tenant). */
  @Column(nullable = false, length = 63, unique = true)
  private String slug;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Transient private boolean isNew = true;

  protected Tenant() {}

  public Tenant(UUID id, String name, String slug) {
    this.id = id;
    this.name = name;
    this.slug = slug;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }
}
