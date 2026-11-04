package io.github.dimasnurfauzi.invoicing.platform.controller;

import io.github.dimasnurfauzi.invoicing.auth.dto.LoginResponse;
import io.github.dimasnurfauzi.invoicing.platform.dto.PlatformLoginRequest;
import io.github.dimasnurfauzi.invoicing.platform.service.PlatformAuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Platform admin")
public class PlatformAuthController {

  private final PlatformAuthService authService;

  public PlatformAuthController(PlatformAuthService authService) {
    this.authService = authService;
  }

  /** Issues a platform token: valid only under /api/platform/**, never on the tenant API. */
  @PostMapping("/api/platform/auth/login")
  @SecurityRequirements // public
  public LoginResponse login(@Valid @RequestBody PlatformLoginRequest request) {
    return authService.login(request);
  }
}
