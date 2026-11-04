package io.github.dimasnurfauzi.invoicing.supportaccess;

import io.github.dimasnurfauzi.invoicing.auth.UserRepository;
import io.github.dimasnurfauzi.invoicing.auth.UserResponse;
import io.github.dimasnurfauzi.invoicing.common.ForbiddenException;
import io.github.dimasnurfauzi.invoicing.common.NotFoundException;
import io.github.dimasnurfauzi.invoicing.common.PageResponse;
import io.github.dimasnurfauzi.invoicing.customer.CustomerResponse;
import io.github.dimasnurfauzi.invoicing.customer.CustomerService;
import io.github.dimasnurfauzi.invoicing.tenant.TenantContext;
import io.github.dimasnurfauzi.invoicing.tenant.TenantRepository;
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
  private final TransactionTemplate writeTransaction;
  private final TransactionTemplate readOnlyTransaction;
  private final Clock clock = Clock.systemUTC();

  public PlatformSupportService(
      TenantRepository tenants,
      SupportAccessGrantRepository grants,
      SupportAccessLogRepository logs,
      UserRepository users,
      CustomerService customers,
      PlatformTransactionManager transactionManager) {
    this.tenants = tenants;
    this.grants = grants;
    this.logs = logs;
    this.users = users;
    this.customers = customers;
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
