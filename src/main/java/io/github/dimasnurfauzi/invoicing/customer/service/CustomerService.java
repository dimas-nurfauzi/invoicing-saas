package io.github.dimasnurfauzi.invoicing.customer.service;

import io.github.dimasnurfauzi.invoicing.common.dto.PageResponse;
import io.github.dimasnurfauzi.invoicing.common.exception.ConflictException;
import io.github.dimasnurfauzi.invoicing.common.exception.NotFoundException;
import io.github.dimasnurfauzi.invoicing.customer.dto.CustomerRequest;
import io.github.dimasnurfauzi.invoicing.customer.dto.CustomerResponse;
import io.github.dimasnurfauzi.invoicing.customer.entity.Customer;
import io.github.dimasnurfauzi.invoicing.customer.repository.CustomerRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers of the caller's tenant. No method takes a tenant id: every repository call is scoped by
 * the tenant filter, so another tenant's customer is simply "not found".
 */
@Service
public class CustomerService {

  /** Name, then id as a tie-breaker so paging is stable. */
  public static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));

  private final CustomerRepository customers;
  private final Clock clock = Clock.systemUTC();

  public CustomerService(CustomerRepository customers) {
    this.customers = customers;
  }

  @Transactional
  public CustomerResponse create(CustomerRequest request) {
    Customer customer =
        new Customer(
            request.name().trim(),
            normalizeEmail(request.email()),
            blankToNull(request.phone()),
            blankToNull(request.billingAddress()),
            blankToNull(request.taxId()));
    return CustomerResponse.from(customers.save(customer));
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> list(
      String query, boolean includeArchived, int page, int size) {
    return PageResponse.from(
        search(query, includeArchived, PageRequest.of(page, size, DEFAULT_SORT)),
        CustomerResponse::from);
  }

  /** Shared with the support view; must run with the target tenant in context. */
  public Page<Customer> search(String query, boolean includeArchived, Pageable pageable) {
    return customers.search(likePattern(query), includeArchived, pageable);
  }

  @Transactional(readOnly = true)
  public CustomerResponse get(UUID id) {
    return CustomerResponse.from(find(id));
  }

  @Transactional
  public CustomerResponse update(UUID id, CustomerRequest request) {
    Customer customer = find(id);
    if (customer.isArchived()) {
      throw new ConflictException("Customer is archived; restore it before editing");
    }
    customer.update(
        request.name().trim(),
        normalizeEmail(request.email()),
        blankToNull(request.phone()),
        blankToNull(request.billingAddress()),
        blankToNull(request.taxId()));
    // Flush so updatedAt in the response reflects this update.
    return CustomerResponse.from(customers.saveAndFlush(customer));
  }

  @Transactional
  public void archive(UUID id) {
    find(id).archive(clock.instant());
  }

  @Transactional
  public CustomerResponse restore(UUID id) {
    Customer customer = find(id);
    customer.restore();
    return CustomerResponse.from(customers.saveAndFlush(customer));
  }

  private Customer find(UUID id) {
    return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer"));
  }

  /**
   * Lowercased substring pattern; {@code ! % _} in the query are escaped (escape char {@code !}).
   */
  static String likePattern(String query) {
    if (query == null || query.isBlank()) {
      return "%";
    }
    String escaped =
        query
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    return "%" + escaped + "%";
  }

  private static String normalizeEmail(String email) {
    String value = blankToNull(email);
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
