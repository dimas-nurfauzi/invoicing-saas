package io.github.dimasnurfauzi.invoicing.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tenants is not tenant-owned, so these queries are not (and need not be) tenant-filtered. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  boolean existsBySlug(String slug);

  Optional<Tenant> findBySlug(String slug);

  List<Tenant> findAllByOrderByCreatedAtDesc();

  /** Per-request suspension check: one primary-key lookup, no entity loaded. */
  @Query("SELECT t.status FROM Tenant t WHERE t.id = :id")
  Optional<TenantStatus> findStatusById(@Param("id") UUID id);

  /** For jobs that run per tenant (see {@code TenantContext.callAs}). */
  @Query("SELECT t.id FROM Tenant t ORDER BY t.createdAt")
  List<UUID> findAllIds();
}
