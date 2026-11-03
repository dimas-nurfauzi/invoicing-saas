package io.github.dimasnurfauzi.invoicing.tenant;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantRegistrationController {

  private final TenantRegistrationService registrationService;

  public TenantRegistrationController(TenantRegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TenantRegistrationResponse register(
      @Valid @RequestBody TenantRegistrationRequest request) {
    return registrationService.register(request);
  }
}
