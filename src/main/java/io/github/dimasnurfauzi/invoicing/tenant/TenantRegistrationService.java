package io.github.dimasnurfauzi.invoicing.tenant;

import io.github.dimasnurfauzi.invoicing.auth.Role;
import io.github.dimasnurfauzi.invoicing.auth.User;
import io.github.dimasnurfauzi.invoicing.auth.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantRegistrationService {

  private final TenantRepository tenants;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public TenantRegistrationService(
      TenantRepository tenants, UserRepository users, PasswordEncoder passwordEncoder) {
    this.tenants = tenants;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  /** Creates the tenant and its first ADMIN user atomically. */
  @Transactional
  public TenantRegistrationResponse register(TenantRegistrationRequest request) {
    String slug = request.tenantSlug();
    // Fast path for a friendly error; the unique constraint below is what actually guarantees it.
    if (tenants.existsBySlug(slug)) {
      throw new SlugAlreadyTakenException(slug);
    }

    Tenant tenant;
    try {
      tenant = tenants.saveAndFlush(new Tenant(request.tenantName().trim(), slug));
    } catch (DataIntegrityViolationException e) {
      throw new SlugAlreadyTakenException(slug);
    }

    // tenant_id comes from the tenant just created, never from the request.
    User admin =
        users.save(
            new User(
                tenant.getId(),
                request.adminEmail().trim().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(request.adminPassword()),
                Role.ADMIN));

    return new TenantRegistrationResponse(
        tenant.getId(), tenant.getSlug(), admin.getId(), admin.getEmail(), admin.getRole());
  }
}
