package io.github.dimasnurfauzi.invoicing.invoice;

import io.github.dimasnurfauzi.invoicing.tenant.TenantContext;
import io.github.dimasnurfauzi.invoicing.tenant.TenantRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Marks SENT invoices whose due date has passed as OVERDUE, once a day (cron and zone from {@code
 * app.overdue.*}; cron "-" disables it, as in tests).
 *
 * <p>There is no request, so no tenant context: the job lists tenants and runs each one inside
 * {@link TenantContext#callAs}, going through the normal tenant filter (no bypass). Each batch is
 * its own transaction and locks its rows, so it never overwrites a payment that lands at the same
 * time. Idempotent: running twice changes nothing the second time. If several app instances run it,
 * the row locks make that safe too (just redundant).
 */
@Component
public class OverdueInvoiceJob {

  static final int BATCH_SIZE = 200;

  private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceJob.class);

  private final TenantRepository tenants;
  private final InvoiceRepository invoices;
  private final TransactionTemplate transaction;
  private final ZoneId zone;

  public OverdueInvoiceJob(
      TenantRepository tenants,
      InvoiceRepository invoices,
      PlatformTransactionManager transactionManager,
      @Value("${app.overdue.zone:Asia/Jakarta}") String zone) {
    this.tenants = tenants;
    this.invoices = invoices;
    this.transaction = new TransactionTemplate(transactionManager);
    this.zone = ZoneId.of(zone);
  }

  @Scheduled(cron = "${app.overdue.cron:0 5 0 * * *}", zone = "${app.overdue.zone:Asia/Jakarta}")
  public void run() {
    markOverdue(LocalDate.now(zone));
  }

  /** Marks invoices due before {@code today} in every tenant; returns how many changed. */
  public int markOverdue(LocalDate today) {
    int total = 0;
    for (UUID tenantId : tenants.findAllIds()) {
      int marked = TenantContext.callAs(tenantId, () -> markOverdueInCurrentTenant(today));
      if (marked > 0) {
        log.info("Marked {} invoice(s) overdue for tenant {}", marked, tenantId);
      }
      total += marked;
    }
    return total;
  }

  private int markOverdueInCurrentTenant(LocalDate today) {
    int marked = 0;
    while (true) {
      Integer batch =
          transaction.execute(status -> {
            List<Invoice> due =
                invoices.lockOverdueCandidates(today, PageRequest.of(0, BATCH_SIZE));
            due.forEach(Invoice::markOverdue);
            return due.size();
          });
      marked += batch;
      if (batch < BATCH_SIZE) {
        return marked;
      }
    }
  }
}
