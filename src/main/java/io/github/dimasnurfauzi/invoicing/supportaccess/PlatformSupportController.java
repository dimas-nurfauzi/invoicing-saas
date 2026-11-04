package io.github.dimasnurfauzi.invoicing.supportaccess;

import io.github.dimasnurfauzi.invoicing.auth.UserResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only support views of a tenant's data, for platform admins (enforced by the platform
 * security chain). Each call needs a reason (e.g. a ticket number), an active grant from the
 * tenant, and is logged for the tenant to see.
 */
@RestController
@RequestMapping("/api/platform/tenants/{tenantId}/support")
@Tag(name = "Platform admin")
public class PlatformSupportController {

  private final PlatformSupportService service;

  public PlatformSupportController(PlatformSupportService service) {
    this.service = service;
  }

  @GetMapping("/users")
  public List<UserResponse> users(
      @PathVariable UUID tenantId,
      @RequestParam @NotBlank @Size(min = 3, max = 200) String reason,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.listUsers(tenantId, UUID.fromString(jwt.getSubject()), reason.trim());
  }
}
