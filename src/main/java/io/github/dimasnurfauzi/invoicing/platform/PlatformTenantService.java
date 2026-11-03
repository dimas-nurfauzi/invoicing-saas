package io.github.dimasnurfauzi.invoicing.platform;

import io.github.dimasnurfauzi.invoicing.common.NotFoundException;
import io.github.dimasnurfauzi.invoicing.tenant.Tenant;
import io.github.dimasnurfauzi.invoicing.tenant.TenantRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant administration for platform admins. Works only on the tenants table (not tenant-owned), so
 * it never needs to bypass the tenant filter.
 */
@Service
public class PlatformTenantService {

  private static final Logger log = LoggerFactory.getLogger(PlatformTenantService.class);

  private final TenantRepository tenants;
  private final Clock clock = Clock.systemUTC();

  public PlatformTenantService(TenantRepository tenants) {
    this.tenants = tenants;
  }

  @Transactional(readOnly = true)
  public List<PlatformTenantResponse> list() {
    return tenants.findAllByOrderByCreatedAtDesc().stream()
        .map(PlatformTenantResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PlatformTenantResponse get(UUID id) {
    return PlatformTenantResponse.from(find(id));
  }

  @Transactional
  public PlatformTenantResponse suspend(UUID id, UUID adminId) {
    Tenant tenant = find(id);
    tenant.suspend(clock.instant());
    log.info("Platform admin {} suspended tenant {}", adminId, id);
    return PlatformTenantResponse.from(tenant);
  }

  @Transactional
  public PlatformTenantResponse activate(UUID id, UUID adminId) {
    Tenant tenant = find(id);
    tenant.activate();
    log.info("Platform admin {} activated tenant {}", adminId, id);
    return PlatformTenantResponse.from(tenant);
  }

  private Tenant find(UUID id) {
    return tenants.findById(id).orElseThrow(() -> new NotFoundException("Tenant"));
  }
}
