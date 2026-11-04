package io.github.dimasnurfauzi.invoicing.supportaccess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped to the current tenant by {@code @TenantId}; no method takes a tenant id. */
public interface SupportAccessGrantRepository extends JpaRepository<SupportAccessGrant, UUID> {

  List<SupportAccessGrant> findAllByOrderByCreatedAtDesc();

  @Query(
      "SELECT g FROM SupportAccessGrant g WHERE g.revokedAt IS NULL AND g.expiresAt > :now"
          + " ORDER BY g.expiresAt DESC")
  List<SupportAccessGrant> findActive(@Param("now") Instant now);
}
