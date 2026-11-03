package io.github.dimasnurfauzi.invoicing.tenant;

import io.github.dimasnurfauzi.invoicing.common.ForbiddenException;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-service signup. Can be switched off with {@code TENANT_SIGNUP_ENABLED=false}; tenants
 * are then created only by a platform admin.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantRegistrationController {

  private final TenantRegistrationService registrationService;
  private final boolean signupEnabled;

  public TenantRegistrationController(
      TenantRegistrationService registrationService,
      @Value("${app.tenant-signup-enabled:true}") boolean signupEnabled) {
    this.registrationService = registrationService;
    this.signupEnabled = signupEnabled;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements // public
  public TenantRegistrationResponse register(
      @Valid @RequestBody TenantRegistrationRequest request) {
    if (!signupEnabled) {
      throw new ForbiddenException("Tenant signup is disabled");
    }
    return registrationService.register(request);
  }
}
