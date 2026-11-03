package io.github.dimasnurfauzi.invoicing.platform;

import io.github.dimasnurfauzi.invoicing.tenant.TenantRegistrationRequest;
import io.github.dimasnurfauzi.invoicing.tenant.TenantRegistrationResponse;
import io.github.dimasnurfauzi.invoicing.tenant.TenantRegistrationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform admin only (enforced by the platform security chain). */
@RestController
@RequestMapping("/api/platform/tenants")
@Tag(name = "Platform admin")
public class PlatformTenantController {

  private final PlatformTenantService tenantService;
  private final TenantRegistrationService registrationService;

  public PlatformTenantController(
      PlatformTenantService tenantService, TenantRegistrationService registrationService) {
    this.tenantService = tenantService;
    this.registrationService = registrationService;
  }

  @GetMapping
  public List<PlatformTenantResponse> list() {
    return tenantService.list();
  }

  @GetMapping("/{id}")
  public PlatformTenantResponse get(@PathVariable UUID id) {
    return tenantService.get(id);
  }

  /** Creates a tenant and its first ADMIN; works even when public signup is disabled. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TenantRegistrationResponse create(@Valid @RequestBody TenantRegistrationRequest request) {
    return registrationService.register(request);
  }

  @PostMapping("/{id}/suspend")
  public PlatformTenantResponse suspend(
      @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return tenantService.suspend(id, UUID.fromString(jwt.getSubject()));
  }

  @PostMapping("/{id}/activate")
  public PlatformTenantResponse activate(
      @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return tenantService.activate(id, UUID.fromString(jwt.getSubject()));
  }
}
