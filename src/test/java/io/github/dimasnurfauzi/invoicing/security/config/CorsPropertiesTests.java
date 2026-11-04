package io.github.dimasnurfauzi.invoicing.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTests {

  @Test
  void emptyOrMissingMeansDisabled() {
    assertThat(new CorsProperties(null).enabled()).isFalse();
    assertThat(new CorsProperties(List.of()).enabled()).isFalse();
    assertThat(new CorsProperties(List.of(" ", "")).enabled()).isFalse();
  }

  @Test
  void acceptsExactOriginsAndTrims() {
    CorsProperties props =
        new CorsProperties(List.of(" http://localhost:5173 ", "https://demo.example.com"));

    assertThat(props.allowedOrigins())
        .containsExactly("http://localhost:5173", "https://demo.example.com");
    assertThat(props.enabled()).isTrue();
  }

  @Test
  void rejectsWildcardsPathsAndNonHttpOrigins() {
    for (String bad :
        List.of(
            "*", "https://*.example.com", "http://localhost:5173/", "localhost:5173", "ftp://x")) {
      assertThatThrownBy(() -> new CorsProperties(List.of(bad)))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }
  }
}
