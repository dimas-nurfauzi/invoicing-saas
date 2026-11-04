package io.github.dimasnurfauzi.invoicing.customer;

import io.github.dimasnurfauzi.invoicing.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customers of the caller's tenant. Any role may create, read and edit; only ADMIN archives. */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
    return service.create(request);
  }

  @GetMapping
  public PageResponse<CustomerResponse> list(
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(q, includeArchived, page, size);
  }

  @GetMapping("/{id}")
  public CustomerResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  public CustomerResponse update(
      @PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
    return service.update(id, request);
  }

  /** Archives (soft delete); the customer stays readable by id and can be restored. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void archive(@PathVariable UUID id) {
    service.archive(id);
  }

  @PostMapping("/{id}/restore")
  @PreAuthorize("hasRole('ADMIN')")
  public CustomerResponse restore(@PathVariable UUID id) {
    return service.restore(id);
  }
}
