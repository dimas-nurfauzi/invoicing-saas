package io.github.dimasnurfauzi.invoicing.payment.entity;

import io.github.dimasnurfauzi.invoicing.invoice.entity.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

/** Money received against an invoice. Tenant-owned and append-only (never updated or deleted). */
@Entity
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  private Invoice invoice;

  @Column(nullable = false, precision = 19, scale = 2, updatable = false)
  private BigDecimal amount;

  @Column(name = "paid_on", nullable = false, updatable = false)
  private LocalDate paidOn;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private PaymentMethod method;

  @Column(length = 100, updatable = false)
  private String reference;

  @Column(length = 500, updatable = false)
  private String note;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Payment() {}

  /** Created only by {@code PaymentService}, under the invoice row lock. */
  public Payment(
      Invoice invoice,
      BigDecimal amount,
      LocalDate paidOn,
      PaymentMethod method,
      String reference,
      String note,
      UUID recordedBy) {
    this.invoice = invoice;
    this.amount = amount;
    this.paidOn = paidOn;
    this.method = method;
    this.reference = reference;
    this.note = note;
    this.recordedBy = recordedBy;
  }

  public UUID getId() {
    return id;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getPaidOn() {
    return paidOn;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public String getReference() {
    return reference;
  }

  public String getNote() {
    return note;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
