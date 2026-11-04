package io.github.dimasnurfauzi.invoicing.supportaccess.service;

import io.github.dimasnurfauzi.invoicing.common.dto.PageResponse;
import io.github.dimasnurfauzi.invoicing.common.exception.ForbiddenException;
import io.github.dimasnurfauzi.invoicing.common.exception.NotFoundException;
import io.github.dimasnurfauzi.invoicing.customer.dto.CustomerResponse;
import io.github.dimasnurfauzi.invoicing.customer.service.CustomerService;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceResponse;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceSummaryResponse;
import io.github.dimasnurfauzi.invoicing.invoice.service.InvoiceService;
import io.github.dimasnurfauzi.invoicing.payment.dto.PaymentResponse;
import io.github.dimasnurfauzi.invoicing.payment.service.PaymentService;
import io.github.dimasnurfauzi.invoicing.supportaccess.entity.SupportAccessGrant;
import io.github.dimasnurfauzi.invoicing.supportaccess.entity.SupportAccessLog;
import io.github.dimasnurfauzi.invoicing.supportaccess.repository.SupportAccessGrantRepository;
import io.github.dimasnurfauzi.invoicing.supportaccess.repository.SupportAccessLogRepository;
import io.github.dimasnurfauzi.invoicing.tenant.context.TenantContext;
import io.github.dimasnurfauzi.invoicing.tenant.repository.TenantRepository;
import io.github.dimasnurfauzi.invoicing.user.dto.UserResponse;
import io.github.dimasnurfauzi.invoicing.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Platform side of support access. A platform admin reads a tenant's data only through {@link
 * #readAs}, which:
 *
 * <ol>
 *   <li>requires an active grant from that tenant (else 403),
 *   <li>writes the access log first, in its own transaction (if logging fails, nothing is read),
 *   <li>reads inside {@code TenantContext.callAs(tenantId)} and a read-only transaction, which
 *       PostgreSQL enforces ({@code BEGIN READ ONLY}): any write is rejected by the database.
 * </ol>
 *
 * It uses the normal tenant filter for the target tenant; there is no bypass. Not
 * {@code @Transactional}: the tenant context must be set before each transaction opens.
 */
@Service
public class PlatformSupportService {

  private static final Logger log = LoggerFactory.getLogger(PlatformSupportService.class);

  private final TenantRepository tenants;
  private final SupportAccessGrantRepository grants;
  private final SupportAccessLogRepository logs;
  private final UserRepository users;
  private final CustomerService customers;
  private final InvoiceService invoices;
  private final PaymentService payments;
  private final TransactionTemplate writeTransaction;
  private final TransactionTemplate readOnlyTransaction;
  private final Clock clock = Clock.systemUTC();

  public PlatformSupportService(
      TenantRepository tenants,
      SupportAccessGrantRepository grants,
      SupportAccessLogRepository logs,
      UserRepository users,
      CustomerService customers,
      InvoiceService invoices,
      PaymentService payments,
      PlatformTransactionManager transactionManager) {
    this.tenants = tenants;
    this.grants = grants;
    this.logs = logs;
    this.users = users;
    this.customers = customers;
    this.invoices = invoices;
    this.payments = payments;
    this.writeTransaction = new TransactionTemplate(transactionManager);
    this.readOnlyTransaction = new TransactionTemplate(transactionManager);
    this.readOnlyTransaction.setReadOnly(true);
  }

  public List<UserResponse> listUsers(UUID tenantId, UUID platformAdminId, String reason) {
    return readAs(
        tenantId,
        platformAdminId,
        reason,
        "GET users",
        () ->
            users.findAllByOrderByCreatedAtAscEmailAsc().stream().map(UserResponse::from).toList());
  }

  /** All customers of the tenant, archived included (support needs the full picture). */
  public PageResponse<CustomerResponse> listCustomers(
      UUID tenantId, UUID platformAdminId, String reason, int page, int size) {
    return readAs(
        tenantId,
        platformAdminId,
        reason,
        "GET customers",
        () ->
            PageResponse.from(
                customers.search(
                    null, true, PageRequest.of(page, size, CustomerService.DEFAULT_SORT)),
                CustomerResponse::from));
  }

  public PageResponse<InvoiceSummaryResponse> listInvoices(
      UUID tenantId, UUID platformAdminId, String reason, int page, int size) {
    return readAs(
        tenantId,
        platformAdminId,
        reason,
        "GET invoices",
        () -> invoices.list(null, null, page, size));
  }

  public InvoiceResponse getInvoice(
      UUID tenantId, UUID platformAdminId, String reason, UUID invoiceId) {
    return readAs(
        tenantId,
        platformAdminId,
        reason,
        "GET invoice " + invoiceId,
        () -> invoices.get(invoiceId));
  }

  public List<PaymentResponse> listPayments(
      UUID tenantId, UUID platformAdminId, String reason, UUID invoiceId) {
    return readAs(
        tenantId,
        platformAdminId,
        reason,
        "GET payments of invoice " + invoiceId,
        () -> payments.list(invoiceId));
  }

  /** The only way platform code may read tenant-owned data. */
  public <T> T readAs(
      UUID tenantId, UUID platformAdminId, String reason, String resource, Supplier<T> read) {
    if (!tenants.existsById(tenantId)) {
      throw new NotFoundException("Tenant");
    }
    return TenantContext.callAs(
        tenantId,
        () -> {
          writeTransaction.executeWithoutResult(status -> {
            Instant now = clock.instant();
            SupportAccessGrant grant =
                grants.findActive(now).stream()
                    .findFirst()
                    .orElseThrow(() ->
                        new ForbiddenException("No active support access grant for this tenant"));
            logs.save(new SupportAccessLog(grant.getId(), platformAdminId, reason, resource, now));
          });
          log.info("Platform admin {} read '{}' of tenant {}", platformAdminId, resource, tenantId);
          return readOnlyTransaction.execute(status -> read.get());
        });
  }
}
