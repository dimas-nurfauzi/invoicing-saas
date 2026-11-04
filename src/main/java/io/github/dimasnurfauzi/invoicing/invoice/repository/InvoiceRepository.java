package io.github.dimasnurfauzi.invoicing.invoice.repository;

import io.github.dimasnurfauzi.invoicing.invoice.entity.Invoice;
import io.github.dimasnurfauzi.invoicing.invoice.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /** {@code SELECT ... FOR UPDATE} on one invoice, so two issue/void requests can't both win. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Invoice i WHERE i.id = :id")
  Optional<Invoice> lockById(@Param("id") UUID id);

  /**
   * Invoices in {@code status} (the job passes SENT) past their due date, locked ({@code FOR
   * UPDATE}). Under READ COMMITTED, PostgreSQL re-checks the WHERE after waiting for a lock, so an
   * invoice paid meanwhile (now PAID) drops out instead of being overwritten.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT i FROM Invoice i WHERE i.status = :status AND i.dueDate < :today"
          + " ORDER BY i.dueDate, i.id")
  List<Invoice> lockOverdueCandidates(
      @Param("status") InvoiceStatus status, @Param("today") LocalDate today, Pageable limit);
}
