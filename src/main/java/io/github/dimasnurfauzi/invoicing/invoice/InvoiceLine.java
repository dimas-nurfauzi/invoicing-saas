package io.github.dimasnurfauzi.invoicing.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * One line of an invoice. Tenant-owned like its invoice. Amounts are computed by {@link
 * InvoiceCalculator} when the line is created and stored as a snapshot.
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  private Invoice invoice;

  @Column(name = "line_no", nullable = false)
  private int lineNo;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(nullable = false, precision = 19, scale = 3)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal discountPercent;

  @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal taxRatePercent;

  @Column(name = "line_subtotal", nullable = false, precision = 19, scale = 2)
  private BigDecimal lineSubtotal;

  @Column(name = "line_discount", nullable = false, precision = 19, scale = 2)
  private BigDecimal lineDiscount;

  @Column(name = "line_tax", nullable = false, precision = 19, scale = 2)
  private BigDecimal lineTax;

  @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
  private BigDecimal lineTotal;

  protected InvoiceLine() {}

  InvoiceLine(Invoice invoice, int lineNo, InvoiceLineDraft draft) {
    this.invoice = invoice;
    this.lineNo = lineNo;
    this.description = draft.description();
    // Same scale as the columns, so the response is identical before and after a reload. Input is
    // validated to at most these decimals, so UNNECESSARY never has to round.
    this.quantity = draft.quantity().setScale(3, RoundingMode.UNNECESSARY);
    this.unitPrice = draft.unitPrice().setScale(2, RoundingMode.UNNECESSARY);
    this.discountPercent = draft.discountPercent().setScale(2, RoundingMode.UNNECESSARY);
    this.taxRatePercent = draft.taxRatePercent().setScale(2, RoundingMode.UNNECESSARY);
    InvoiceCalculator.LineAmounts amounts =
        InvoiceCalculator.line(quantity, unitPrice, discountPercent, taxRatePercent);
    this.lineSubtotal = amounts.subtotal();
    this.lineDiscount = amounts.discount();
    this.lineTax = amounts.tax();
    this.lineTotal = amounts.total();
  }

  InvoiceCalculator.LineAmounts amounts() {
    return new InvoiceCalculator.LineAmounts(lineSubtotal, lineDiscount, lineTax, lineTotal);
  }

  public UUID getId() {
    return id;
  }

  public int getLineNo() {
    return lineNo;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getDiscountPercent() {
    return discountPercent;
  }

  public BigDecimal getTaxRatePercent() {
    return taxRatePercent;
  }

  public BigDecimal getLineSubtotal() {
    return lineSubtotal;
  }

  public BigDecimal getLineDiscount() {
    return lineDiscount;
  }

  public BigDecimal getLineTax() {
    return lineTax;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }
}
