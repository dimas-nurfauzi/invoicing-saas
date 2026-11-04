package io.github.dimasnurfauzi.invoicing.invoice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.dimasnurfauzi.invoicing.invoice.entity.Invoice;
import io.github.dimasnurfauzi.invoicing.invoice.entity.InvoiceLine;
import io.github.dimasnurfauzi.invoicing.invoice.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full invoice with lines. Money is serialized as JSON strings ("1500000.00") so JavaScript clients
 * don't lose precision; no tenant id.
 */
public record InvoiceResponse(
    UUID id,
    String invoiceNumber,
    InvoiceStatus status,
    CustomerRef customer,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    String notes,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountTotal,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxTotal,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountPaid,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceDue,
    Instant paidAt,
    List<Line> lines,
    BillTo billTo,
    Instant issuedAt,
    Instant voidedAt,
    String voidReason,
    Instant createdAt,
    Instant updatedAt) {

  public record CustomerRef(UUID id, String name) {}

  /** Customer data frozen at issue time; null for drafts. */
  public record BillTo(String name, String email, String address, String taxId) {}

  public record Line(
      int lineNo,
      String description,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountPercent,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxRatePercent,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tax,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {

    static Line from(InvoiceLine line) {
      return new Line(
          line.getLineNo(),
          line.getDescription(),
          line.getQuantity(),
          line.getUnitPrice(),
          line.getDiscountPercent(),
          line.getTaxRatePercent(),
          line.getLineSubtotal(),
          line.getLineDiscount(),
          line.getLineTax(),
          line.getLineTotal());
    }
  }

  public static InvoiceResponse from(Invoice invoice) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getInvoiceNumber(),
        invoice.getStatus(),
        new CustomerRef(invoice.getCustomer().getId(), invoice.getCustomer().getName()),
        invoice.getCurrency(),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getNotes(),
        invoice.getSubtotal(),
        invoice.getDiscountTotal(),
        invoice.getTaxTotal(),
        invoice.getTotal(),
        invoice.getAmountPaid(),
        invoice.getBalanceDue(),
        invoice.getPaidAt(),
        invoice.getLines().stream().map(Line::from).toList(),
        invoice.getIssuedAt() == null
            ? null
            : new BillTo(
                invoice.getBillToName(),
                invoice.getBillToEmail(),
                invoice.getBillToAddress(),
                invoice.getBillToTaxId()),
        invoice.getIssuedAt(),
        invoice.getVoidedAt(),
        invoice.getVoidReason(),
        invoice.getCreatedAt(),
        invoice.getUpdatedAt());
  }
}
