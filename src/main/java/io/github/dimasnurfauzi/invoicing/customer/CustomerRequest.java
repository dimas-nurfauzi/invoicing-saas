package io.github.dimasnurfauzi.invoicing.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create and full update (PUT). No tenant id: customers always belong to the caller's tenant. */
public record CustomerRequest(
    @NotBlank @Size(max = 200) String name,
    @Email @Size(max = 254) String email,
    @Pattern(regexp = "^$|^[0-9+()\\-. ]{3,30}$", message = "must be a phone number") String phone,
    @Size(max = 500) String billingAddress,
    @Size(max = 30) String taxId) {}
