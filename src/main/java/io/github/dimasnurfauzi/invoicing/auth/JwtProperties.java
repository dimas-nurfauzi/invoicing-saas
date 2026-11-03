package io.github.dimasnurfauzi.invoicing.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code app.jwt.*}. The secret comes from the {@code JWT_SECRET} env var
 * and is never logged.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

  /** HS256 needs a key of at least 256 bits. */
  static final int MIN_SECRET_BYTES = 32;

  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "app.jwt.secret (JWT_SECRET) must be at least %d bytes".formatted(MIN_SECRET_BYTES));
    }
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("app.jwt.ttl (JWT_TTL) must be a positive duration");
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("app.jwt.issuer must not be blank");
    }
  }

  byte[] secretBytes() {
    return secret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "JwtProperties[secret=***, ttl=%s, issuer=%s]".formatted(ttl, issuer);
  }
}
