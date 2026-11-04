package io.github.dimasnurfauzi.invoicing.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

/**
 * A login identity. Tenant-owned: {@code @TenantId} makes Hibernate filter every query by the
 * current tenant and fill tenant_id on insert. Email is unique only within its tenant.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {}

  /** tenant_id is not a parameter: Hibernate sets it from the current tenant on insert. */
  public User(String email, String passwordHash, Role role) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String toString() {
    return "User[id=%s, tenantId=%s, role=%s]".formatted(id, tenantId, role);
  }
}
