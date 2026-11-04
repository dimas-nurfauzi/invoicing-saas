package io.github.dimasnurfauzi.invoicing.tenant.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimasnurfauzi.invoicing.common.exception.ForbiddenException;
import io.github.dimasnurfauzi.invoicing.tenant.dto.TenantRegistrationRequest;
import org.junit.jupiter.api.Test;

/**
 * The signup toggle, without a Spring context (a second context would start a second container).
 */
class TenantRegistrationControllerTests {

  @Test
  void signupDisabledIsForbiddenBeforeTouchingTheService() {
    // A null service proves the request is rejected before registration is attempted.
    TenantRegistrationController controller = new TenantRegistrationController(null, false);

    assertThatThrownBy(() ->
            controller.register(
                new TenantRegistrationRequest(
                    "Toko", "toko-maju", "owner@example.com", "correct-horse-battery")))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Tenant signup is disabled");
  }
}
