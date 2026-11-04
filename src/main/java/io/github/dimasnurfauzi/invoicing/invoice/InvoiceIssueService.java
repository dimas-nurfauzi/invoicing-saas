package io.github.dimasnurfauzi.invoicing.invoice;

import io.github.dimasnurfauzi.invoicing.common.ConflictException;
import io.github.dimasnurfauzi.invoicing.common.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issuing and voiding invoices. Numbers are gapless and unique per (tenant, year):
 *
 * <ol>
 *   <li>Make sure the counter row for the year exists (own short transaction; a concurrent creator
 *       losing the unique-constraint race is fine).
 *   <li>One transaction: lock the invoice ({@code FOR UPDATE}), check it's a draft, lock the
 *       counter ({@code FOR UPDATE}), increment, assign the number, mark SENT. Lock order is always
 *       invoice → counter, so there are no deadlocks. If anything fails, the increment rolls back
 *       with the rest: no gaps. Concurrent issuers wait on the counter lock: no duplicates.
 * </ol>
 *
 * Not {@code @Transactional}: the steps are separate transactions in sequence, so each request
 * holds at most one pooled connection at a time. Everything runs in the caller's tenant context and
 * is tenant-filtered.
 */
@Service
public class InvoiceIssueService {

  private final InvoiceRepository invoices;
  private final InvoiceNumberCounterRepository counters;
  private final TransactionTemplate transaction;
  private final Clock clock = Clock.systemUTC();

  public InvoiceIssueService(
      InvoiceRepository invoices,
      InvoiceNumberCounterRepository counters,
      PlatformTransactionManager transactionManager) {
    this.invoices = invoices;
    this.counters = counters;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public InvoiceResponse issue(UUID id) {
    int year =
        transaction.execute(status -> {
          Invoice invoice = find(id);
          requireIssuable(invoice);
          return invoice.getIssueDate().getYear();
        });
    ensureCounter(year);
    return transaction.execute(status -> {
      Invoice invoice = lock(id);
      // Re-check under the lock: a concurrent request may have issued it meanwhile.
      requireIssuable(invoice);
      InvoiceNumberCounter counter =
          counters.lockByYear(year).orElseThrow(() -> new IllegalStateException("counter"));
      invoice.issue(InvoiceNumbers.format(year, counter.next()), clock.instant());
      return InvoiceResponse.from(invoices.saveAndFlush(invoice));
    });
  }

  public InvoiceResponse voidInvoice(UUID id, String reason) {
    return transaction.execute(status -> {
      Invoice invoice = lock(id);
      if (invoice.isDraft()) {
        throw new ConflictException("Drafts aren't voided; delete the draft instead");
      }
      if (invoice.getAmountPaid().signum() > 0) {
        throw new ConflictException("Invoice has payments recorded; it can't be voided");
      }
      invoice.voidInvoice(reason.trim(), clock.instant());
      return InvoiceResponse.from(invoices.saveAndFlush(invoice));
    });
  }

  private void ensureCounter(int year) {
    if (Boolean.TRUE.equals(transaction.execute(status -> counters.existsByNumberYear(year)))) {
      return;
    }
    try {
      transaction.executeWithoutResult(
          status -> counters.saveAndFlush(new InvoiceNumberCounter(year)));
    } catch (DataIntegrityViolationException e) {
      // Another request created it first (uq_invoice_number_counters). That's all we needed.
    }
  }

  /** Drafts only, and the customer must not be archived. Checked before a number is taken. */
  private static void requireIssuable(Invoice invoice) {
    if (!invoice.getStatus().canTransitionTo(InvoiceStatus.SENT)) {
      throw new IllegalInvoiceTransitionException(invoice.getStatus(), InvoiceStatus.SENT);
    }
    if (invoice.getCustomer().isArchived()) {
      throw new ConflictException("Customer is archived; restore it before issuing");
    }
  }

  private Invoice find(UUID id) {
    return invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice"));
  }

  private Invoice lock(UUID id) {
    return invoices.lockById(id).orElseThrow(() -> new NotFoundException("Invoice"));
  }
}
