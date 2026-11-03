package io.github.dimasnurfauzi.invoicing.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TenantRegistrationTests {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void registersTenantWithFirstAdminAndHashedPassword() {
    String slug = uniqueSlug();

    ResponseEntity<String> response = register("Toko Maju", slug, "Owner@Example.com", PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .contains("\"tenantSlug\":\"" + slug + "\"")
        .contains("\"email\":\"owner@example.com\"")
        .contains("\"role\":\"ADMIN\"")
        .doesNotContainIgnoringCase("password")
        .doesNotContain("$2");

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT u.tenant_id, u.email, u.password_hash, u.role, t.name"
                + " FROM users u JOIN tenants t ON t.id = u.tenant_id WHERE t.slug = ?",
            slug);
    assertThat(row.get("name")).isEqualTo("Toko Maju");
    assertThat(row.get("email")).isEqualTo("owner@example.com");
    assertThat(row.get("role")).isEqualTo("ADMIN");
    String hash = (String) row.get("password_hash");
    assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
  }

  @Test
  void duplicateSlugIsConflictAndCreatesNoUser() {
    String slug = uniqueSlug();
    assertThat(register("First", slug, "a@example.com", PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> second = register("Second", slug, "b@example.com", PASSWORD);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'b@example.com'", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM tenants WHERE slug = ?", Integer.class, slug))
        .isOne();
  }

  @Test
  void sameEmailMayRegisterInDifferentTenants() {
    String email = "shared-" + UUID.randomUUID() + "@example.com";

    assertThat(register("A", uniqueSlug(), email, PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(register("B", uniqueSlug(), email, PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
        .isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void invalidInputIsBadRequest(String name, String slug, String email, String password) {
    ResponseEntity<String> response = register(name, slug, email, password);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  static Object[][] invalidRequests() {
    return new Object[][] {
      {"", "valid-slug", "a@example.com", PASSWORD},
      {"Name", "Bad Slug", "a@example.com", PASSWORD},
      {"Name", "-leading", "a@example.com", PASSWORD},
      {"Name", "ab", "a@example.com", PASSWORD},
      {"Name", "valid-slug", "not-an-email", PASSWORD},
      {"Name", "valid-slug", "a@example.com", "short"},
      {"Name", "valid-slug", "a@example.com", "x".repeat(73)},
    };
  }

  private ResponseEntity<String> register(String name, String slug, String email, String password) {
    var body =
        Map.of(
            "tenantName", name, "tenantSlug", slug, "adminEmail", email, "adminPassword", password);
    return rest.postForEntity("/api/tenants", body, String.class);
  }

  private static String uniqueSlug() {
    return "t-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
