package io.github.dimasnurfauzi.invoicing.invoice;

import io.github.dimasnurfauzi.invoicing.customer.Customer;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An invoice and its lines (one aggregate: lines are only changed through the invoice). Totals are
 * recomputed from the lines on every change and stored, so they can't drift from the lines.
 * Tenant-owned ({@code @TenantId}).
 */
@Entity
@Table(name = "invoices")
public class Invoice {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  @Column(name = "invoice_number", length = 20)
  private String invoiceNumber;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(length = 2000)
  private String notes;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "discount_total", nullable = false, precision = 19, scale = 2)
  private BigDecimal discountTotal;

  @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
  private BigDecimal taxTotal;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal total;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "voided_at")
  private Instant voidedAt;

  @Column(name = "void_reason", length = 500)
  private String voidReason;

  // Snapshot of the customer taken when the invoice is issued.
  @Column(name = "bill_to_name", length = 200)
  private String billToName;

  @Column(name = "bill_to_email", length = 254)
  private String billToEmail;

  @Column(name = "bill_to_address", length = 500)
  private String billToAddress;

  @Column(name = "bill_to_tax_id", length = 30)
  private String billToTaxId;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineNo ASC")
  private List<InvoiceLine> lines = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Invoice() {}

  /** A new DRAFT. tenant_id is set by Hibernate from the current tenant. */
  public Invoice(
      Customer customer,
      String currency,
      LocalDate issueDate,
      LocalDate dueDate,
      String notes,
      List<InvoiceLineDraft> lines) {
    assign(customer, currency, issueDate, dueDate, notes, lines);
  }

  /** Replaces everything editable, lines included. Callers check {@link #isDraft()} first. */
  public void replaceDraft(
      Customer customer,
      String currency,
      LocalDate issueDate,
      LocalDate dueDate,
      String notes,
      List<InvoiceLineDraft> lines) {
    assign(customer, currency, issueDate, dueDate, notes, lines);
  }

  private void assign(
      Customer customer,
      String currency,
      LocalDate issueDate,
      LocalDate dueDate,
      String notes,
      List<InvoiceLineDraft> drafts) {
    this.customer = customer;
    this.currency = currency;
    this.issueDate = issueDate;
    this.dueDate = dueDate;
    this.notes = notes;
    this.lines.clear();
    for (int i = 0; i < drafts.size(); i++) {
      this.lines.add(new InvoiceLine(this, i + 1, drafts.get(i)));
    }
    InvoiceCalculator.Totals totals =
        InvoiceCalculator.totals(this.lines.stream().map(InvoiceLine::amounts).toList());
    this.subtotal = totals.subtotal();
    this.discountTotal = totals.discountTotal();
    this.taxTotal = totals.taxTotal();
    this.total = totals.total();
  }

  public boolean isDraft() {
    return status == InvoiceStatus.DRAFT;
  }

  /**
   * DRAFT → SENT with the given number. Freezes the bill-to data from the current customer. The
   * caller holds the invoice and counter locks.
   */
  void issue(String number, Instant now) {
    transitionTo(InvoiceStatus.SENT);
    this.invoiceNumber = number;
    this.issuedAt = now;
    this.billToName = customer.getName();
    this.billToEmail = customer.getEmail();
    this.billToAddress = customer.getBillingAddress();
    this.billToTaxId = customer.getTaxId();
  }

  /** SENT/OVERDUE → VOID. The number stays used, so the sequence has no gaps. */
  void voidInvoice(String reason, Instant now) {
    transitionTo(InvoiceStatus.VOID);
    this.voidReason = reason;
    this.voidedAt = now;
  }

  private void transitionTo(InvoiceStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new IllegalInvoiceTransitionException(status, next);
    }
    this.status = next;
  }

  public UUID getId() {
    return id;
  }

  public Customer getCustomer() {
    return customer;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public String getCurrency() {
    return currency;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public String getNotes() {
    return notes;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getDiscountTotal() {
    return discountTotal;
  }

  public BigDecimal getTaxTotal() {
    return taxTotal;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getVoidedAt() {
    return voidedAt;
  }

  public String getVoidReason() {
    return voidReason;
  }

  public String getBillToName() {
    return billToName;
  }

  public String getBillToEmail() {
    return billToEmail;
  }

  public String getBillToAddress() {
    return billToAddress;
  }

  public String getBillToTaxId() {
    return billToTaxId;
  }

  public List<InvoiceLine> getLines() {
    return Collections.unmodifiableList(lines);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
