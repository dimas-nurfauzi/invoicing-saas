package io.github.dimasnurfauzi.invoicing.platform.config;

import io.github.dimasnurfauzi.invoicing.platform.entity.PlatformAdmin;
import io.github.dimasnurfauzi.invoicing.platform.repository.PlatformAdminRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first platform admin from {@code PLATFORM_ADMIN_EMAIL} / {@code
 * PLATFORM_ADMIN_PASSWORD} at startup, if that email doesn't exist yet. There is no default account
 * and no public way to become a platform admin. The password is never logged.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

  private final PlatformAdminRepository admins;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;

  public PlatformAdminBootstrap(
      PlatformAdminRepository admins,
      PasswordEncoder passwordEncoder,
      @Value("${app.platform-admin.email:}") String email,
      @Value("${app.platform-admin.password:}") String password) {
    this.admins = admins;
    this.passwordEncoder = passwordEncoder;
    this.email = email.trim().toLowerCase(Locale.ROOT);
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (email.isEmpty() || password.isEmpty()) {
      log.info("PLATFORM_ADMIN_EMAIL/PASSWORD not set; no platform admin bootstrapped");
      return;
    }
    if (password.length() < 12 || password.length() > 72) {
      throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD must be 12 to 72 characters");
    }
    if (admins.existsByEmail(email)) {
      return;
    }
    admins.save(new PlatformAdmin(email, passwordEncoder.encode(password)));
    log.info("Bootstrapped platform admin {}", email);
  }
}
