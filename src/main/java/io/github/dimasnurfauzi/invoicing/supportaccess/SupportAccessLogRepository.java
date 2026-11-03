package io.github.dimasnurfauzi.invoicing.supportaccess;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped to the current tenant by {@code @TenantId}; no method takes a tenant id. */
public interface SupportAccessLogRepository extends JpaRepository<SupportAccessLog, UUID> {

  List<SupportAccessLog> findAllByOrderByAccessedAtDesc();
}
