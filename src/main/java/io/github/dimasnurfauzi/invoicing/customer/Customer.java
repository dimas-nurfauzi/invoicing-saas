package io.github.dimasnurfauzi.invoicing.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Someone a tenant bills. Tenant-owned ({@code @TenantId}). Never hard-deleted: archiving hides it
 * from lists and (later) from new invoices, while existing invoices keep referencing it.
 */
@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 254)
  private String email;

  @Column(length = 30)
  private String phone;

  @Column(name = "billing_address", length = 500)
  private String billingAddress;

  @Column(name = "tax_id", length = 30)
  private String taxId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  protected Customer() {}

  /** tenant_id is not a parameter: Hibernate sets it from the current tenant on insert. */
  public Customer(String name, String email, String phone, String billingAddress, String taxId) {
    assign(name, email, phone, billingAddress, taxId);
  }

  public void update(String name, String email, String phone, String billingAddress, String taxId) {
    assign(name, email, phone, billingAddress, taxId);
  }

  // Private (not final/overridable) so Hibernate's lazy proxies stay safe.
  private void assign(
      String name, String email, String phone, String billingAddress, String taxId) {
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.billingAddress = billingAddress;
    this.taxId = taxId;
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  /** Idempotent: archiving twice keeps the first timestamp. */
  public void archive(Instant now) {
    if (archivedAt == null) {
      archivedAt = now;
    }
  }

  public void restore() {
    archivedAt = null;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getBillingAddress() {
    return billingAddress;
  }

  public String getTaxId() {
    return taxId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
