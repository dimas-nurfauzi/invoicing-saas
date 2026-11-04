package io.github.dimasnurfauzi.invoicing.invoice;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Scoped to the current tenant by {@code @TenantId}; no method takes a tenant id. Specifications
 * build Criteria queries, which the tenant filter covers like JPQL.
 */
public interface InvoiceRepository
    extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

  /** Loads the customer with each invoice so the list doesn't query once per row. */
  @Override
  @EntityGraph(attributePaths = "customer")
  Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);
}
