package io.github.dimasnurfauzi.invoicing.auth;

import io.github.dimasnurfauzi.invoicing.tenant.TenantContext;
import io.github.dimasnurfauzi.invoicing.tenant.TenantRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final TenantRepository tenants;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  /** Compared against when the user doesn't exist, so response time doesn't reveal that. */
  private final String dummyHash;

  public AuthService(
      TenantRepository tenants,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.tenants = tenants;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Pre-authentication: there is no token yet, so the tenant is resolved from the public slug, and
   * the user lookup then runs inside that tenant through the normal tenant filter. Not
   * {@code @Transactional} on purpose: each repository call opens its own session after the tenant
   * context it needs is set.
   */
  public LoginResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    Optional<User> user =
        tenants
            .findBySlug(request.tenantSlug())
            .flatMap(
                tenant -> TenantContext.callAs(tenant.getId(), () -> users.findByEmail(email)));

    String hash = user.map(User::getPasswordHash).orElse(dummyHash);
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
    if (user.isEmpty() || !passwordMatches) {
      throw new InvalidCredentialsException();
    }
    return LoginResponse.bearer(jwtService.issue(user.get()), jwtService.ttlSeconds());
  }
}
