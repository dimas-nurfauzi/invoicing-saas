package io.github.dimasnurfauzi.invoicing.invoice;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped to the current tenant by {@code @TenantId}; no method takes a tenant id. */
public interface InvoiceNumberCounterRepository extends JpaRepository<InvoiceNumberCounter, UUID> {

  boolean existsByNumberYear(int numberYear);

  /** {@code SELECT ... FOR UPDATE}: concurrent issuers for the same tenant and year wait here. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM InvoiceNumberCounter c WHERE c.numberYear = :year")
  Optional<InvoiceNumberCounter> lockByYear(@Param("year") int year);
}
