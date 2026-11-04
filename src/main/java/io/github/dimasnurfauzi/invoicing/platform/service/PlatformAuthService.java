package io.github.dimasnurfauzi.invoicing.platform.service;

import io.github.dimasnurfauzi.invoicing.auth.dto.LoginResponse;
import io.github.dimasnurfauzi.invoicing.auth.exception.InvalidCredentialsException;
import io.github.dimasnurfauzi.invoicing.platform.dto.PlatformLoginRequest;
import io.github.dimasnurfauzi.invoicing.platform.entity.PlatformAdmin;
import io.github.dimasnurfauzi.invoicing.platform.repository.PlatformAdminRepository;
import io.github.dimasnurfauzi.invoicing.security.jwt.JwtService;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuthService {

  private final PlatformAdminRepository admins;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  /** Compared against when the admin doesn't exist, so response time doesn't reveal that. */
  private final String dummyHash;

  public PlatformAuthService(
      PlatformAdminRepository admins, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.admins = admins;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  public LoginResponse login(PlatformLoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    Optional<PlatformAdmin> admin = admins.findByEmail(email);

    String hash = admin.map(PlatformAdmin::getPasswordHash).orElse(dummyHash);
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
    if (admin.isEmpty() || !passwordMatches) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
    return LoginResponse.bearer(
        jwtService.issuePlatformAdmin(admin.get().getId()), jwtService.ttlSeconds());
  }
}
