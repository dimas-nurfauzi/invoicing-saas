package io.github.dimasnurfauzi.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTests {

  private static final String VALID_SECRET = "x".repeat(JwtProperties.MIN_SECRET_BYTES);

  @Test
  void rejectsMissingOrShortSecret() {
    assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1), "iss"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JWT_SECRET");
    assertThatThrownBy(() ->
            new JwtProperties(
                "x".repeat(JwtProperties.MIN_SECRET_BYTES - 1), Duration.ofHours(1), "iss"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveTtl() {
    assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, Duration.ZERO, "iss"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringDoesNotLeakSecret() {
    assertThat(new JwtProperties(VALID_SECRET, Duration.ofHours(1), "iss").toString())
        .doesNotContain(VALID_SECRET);
  }
}
