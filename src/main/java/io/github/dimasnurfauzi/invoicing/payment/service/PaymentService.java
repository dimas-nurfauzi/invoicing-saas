package io.github.dimasnurfauzi.invoicing.payment.service;

import io.github.dimasnurfauzi.invoicing.common.exception.NotFoundException;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceResponse;
import io.github.dimasnurfauzi.invoicing.invoice.entity.Invoice;
import io.github.dimasnurfauzi.invoicing.invoice.repository.InvoiceRepository;
import io.github.dimasnurfauzi.invoicing.payment.dto.PaymentRequest;
import io.github.dimasnurfauzi.invoicing.payment.dto.PaymentResponse;
import io.github.dimasnurfauzi.invoicing.payment.dto.RecordedPaymentResponse;
import io.github.dimasnurfauzi.invoicing.payment.entity.Payment;
import io.github.dimasnurfauzi.invoicing.payment.repository.PaymentRepository;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments of the caller's tenant. Recording locks the invoice row ({@code FOR UPDATE}) so
 * concurrent payments are serialized and can never overpay; the DB CHECK on amount_paid is the last
 * line of defence. Everything is tenant-filtered: another tenant's invoice is "not found".
 */
@Service
public class PaymentService {

  private final InvoiceRepository invoices;
  private final PaymentRepository payments;
  private final Clock clock = Clock.systemUTC();

  public PaymentService(InvoiceRepository invoices, PaymentRepository payments) {
    this.invoices = invoices;
    this.payments = payments;
  }

  @Transactional
  public RecordedPaymentResponse record(UUID invoiceId, PaymentRequest request, UUID recordedBy) {
    Invoice invoice =
        invoices.lockById(invoiceId).orElseThrow(() -> new NotFoundException("Invoice"));
    // Input is validated to at most 2 decimals, so this never rounds.
    var amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
    invoice.applyPayment(amount, clock.instant());
    Payment payment =
        payments.save(
            new Payment(
                invoice,
                amount,
                request.paidOn(),
                request.method(),
                blankToNull(request.reference()),
                blankToNull(request.note()),
                recordedBy));
    invoices.saveAndFlush(invoice);
    return new RecordedPaymentResponse(
        PaymentResponse.from(payment), InvoiceResponse.from(invoice));
  }

  @Transactional(readOnly = true)
  public List<PaymentResponse> list(UUID invoiceId) {
    if (!invoices.existsById(invoiceId)) {
      throw new NotFoundException("Invoice");
    }
    return payments.findByInvoiceIdOrderByPaidOnAscCreatedAtAsc(invoiceId).stream()
        .map(PaymentResponse::from)
        .toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
