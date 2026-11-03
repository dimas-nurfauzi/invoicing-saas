package io.github.dimasnurfauzi.invoicing.tenant;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Feeds {@link TenantContext} to Hibernate's partitioned multi-tenancy. Every entity field marked
 * {@code @TenantId} is then filtered on read and filled on insert by Hibernate itself.
 *
 * <p>Fails closed: with no tenant in context it returns {@link #NO_TENANT}, which matches no row
 * (reads come back empty, inserts violate the tenants foreign key). There is no "root" tenant that
 * bypasses the filter.
 *
 * <p>Hibernate reads this once when a session opens (in practice, at transaction start), so the
 * context must be set before the transaction begins.
 */
@Component
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

  static final UUID NO_TENANT = new UUID(0L, 0L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    return TenantContext.current().orElse(NO_TENANT);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
