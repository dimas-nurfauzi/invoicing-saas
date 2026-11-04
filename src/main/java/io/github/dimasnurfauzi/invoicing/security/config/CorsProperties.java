package io.github.dimasnurfauzi.invoicing.security.config;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins allowed to call the tenant API cross-origin, bound from {@code
 * app.cors.allowed-origins} ({@code CORS_ALLOWED_ORIGINS}, comma-separated). Empty means CORS is
 * off: no cross-origin browser access at all (same as before this setting existed).
 *
 * <p>Only exact origins ({@code scheme://host[:port]}); wildcards are rejected so a typo can't open
 * the API to every site.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  private static final Pattern ORIGIN = Pattern.compile("^https?://[^/\\s*?#]+$");

  public CorsProperties {
    allowedOrigins =
        allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
    for (String origin : allowedOrigins) {
      if (!ORIGIN.matcher(origin).matches()) {
        throw new IllegalArgumentException(
            "app.cors.allowed-origins (CORS_ALLOWED_ORIGINS): '%s' is not an exact origin like http://localhost:5173"
                .formatted(origin));
      }
    }
  }

  public boolean enabled() {
    return !allowedOrigins.isEmpty();
  }
}
