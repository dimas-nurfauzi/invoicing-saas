package io.github.dimasnurfauzi.invoicing.auth;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/api/auth/login")
  @SecurityRequirements // public
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /** The caller's identity, read from the validated token (no database access). */
  @GetMapping("/api/me")
  public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return new MeResponse(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString(JwtClaims.TENANT_ID)),
        Role.valueOf(jwt.getClaimAsString(JwtClaims.ROLE)));
  }

  public record MeResponse(UUID userId, UUID tenantId, Role role) {}
}
