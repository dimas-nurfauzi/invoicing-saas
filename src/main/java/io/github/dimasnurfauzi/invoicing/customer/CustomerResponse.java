package io.github.dimasnurfauzi.invoicing.customer;

import java.time.Instant;
import java.util.UUID;

/** No tenant id in the response. */
public record CustomerResponse(
    UUID id,
    String name,
    String email,
    String phone,
    String billingAddress,
    String taxId,
    boolean archived,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(
        customer.getId(),
        customer.getName(),
        customer.getEmail(),
        customer.getPhone(),
        customer.getBillingAddress(),
        customer.getTaxId(),
        customer.isArchived(),
        customer.getCreatedAt(),
        customer.getUpdatedAt(),
        customer.getArchivedAt());
  }
}
