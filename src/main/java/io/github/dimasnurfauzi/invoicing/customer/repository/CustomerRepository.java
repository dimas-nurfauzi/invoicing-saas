package io.github.dimasnurfauzi.invoicing.customer.repository;

import io.github.dimasnurfauzi.invoicing.customer.entity.Customer;
import io.github.dimasnurfauzi.invoicing.customer.service.CustomerService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped to the current tenant by {@code @TenantId}; no method takes a tenant id. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  /**
   * Case-insensitive search on name or email. {@code pattern} is a LIKE pattern already lowercased
   * and escaped with {@code !} (see {@link CustomerService}); {@code "%"} matches everything.
   */
  @Query(
      """
      SELECT c FROM Customer c
      WHERE (:includeArchived = true OR c.archivedAt IS NULL)
        AND (lower(c.name) LIKE :pattern ESCAPE '!' OR lower(c.email) LIKE :pattern ESCAPE '!')
      """)
  Page<Customer> search(
      @Param("pattern") String pattern,
      @Param("includeArchived") boolean includeArchived,
      Pageable pageable);

  /** Exact, case-insensitive name match among active customers (used to match AI drafts). */
  List<Customer> findByNameIgnoreCaseAndArchivedAtIsNull(String name);
}
