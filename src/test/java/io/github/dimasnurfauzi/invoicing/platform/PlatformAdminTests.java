package io.github.dimasnurfauzi.invoicing.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Platform admin: separate login and token, tenant administration, suspension, and strict
 * separation from the tenant API in both directions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PlatformAdminTests {

  // From src/test/resources/application-test.yml (bootstrapped at startup).
  private static final String ADMIN_EMAIL = "platform-admin@test.local";
  private static final String ADMIN_PASSWORD = "platform-test-password";

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformAdminBootstrap bootstrap;

  private TestTenants api;
  private String platformToken;

  @BeforeEach
  void loginAsPlatformAdmin() {
    api = new TestTenants(rest);
    ResponseEntity<Map> response = platformLogin(ADMIN_EMAIL, ADMIN_PASSWORD);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    platformToken = (String) response.getBody().get("accessToken");
  }

  // --- login and bootstrap ---

  @Test
  void wrongPasswordOrUnknownEmailAreIndistinguishable401s() {
    ResponseEntity<String> wrongPassword =
        rest.postForEntity(
            "/api/platform/auth/login",
            Map.of("email", ADMIN_EMAIL, "password", "wrong-password-123"),
            String.class);
    ResponseEntity<String> unknownEmail =
        rest.postForEntity(
            "/api/platform/auth/login",
            Map.of("email", "nobody@test.local", "password", ADMIN_PASSWORD),
            String.class);

    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownEmail.getBody()).isEqualTo(wrongPassword.getBody());
  }

  @Test
  void bootstrapIsIdempotent() {
    bootstrap.run(new DefaultApplicationArguments());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM platform_admins WHERE email = ?", Integer.class, ADMIN_EMAIL))
        .isOne();
  }

  // --- separation between platform and tenant APIs ---

  @Test
  void platformApiRequiresAToken() {
    assertThat(rest.getForEntity("/api/platform/tenants", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tenantTokenIsRejectedByPlatformApi() {
    TenantSession tenant = api.register("plat-sep");

    ResponseEntity<String> response =
        api.call(HttpMethod.GET, "/api/platform/tenants", tenant.adminToken(), null, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void platformTokenIsRejectedByTenantApi() {
    assertThat(
            api.call(HttpMethod.GET, "/api/users", platformToken, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            api.call(HttpMethod.GET, "/api/me", platformToken, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- tenant administration ---

  @Test
  void listsTenantsWithoutBusinessData() {
    TenantSession tenant = api.register("plat-list");

    ResponseEntity<List> response =
        api.call(HttpMethod.GET, "/api/platform/tenants", platformToken, null, List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map<String, Object>> tenants = response.getBody();
    Map<String, Object> listed =
        tenants.stream().filter(t -> tenant.slug().equals(t.get("slug"))).findFirst().orElseThrow();
    assertThat(listed)
        .containsEntry("id", tenant.tenantId())
        .containsEntry("status", "ACTIVE")
        .containsOnlyKeys("id", "name", "slug", "status", "createdAt", "suspendedAt");
  }

  @Test
  void unknownTenantIs404() {
    assertThat(
            api.call(
                    HttpMethod.GET,
                    "/api/platform/tenants/" + UUID.randomUUID(),
                    platformToken,
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void platformAdminCreatesTenantWhoseAdminCanLogIn() {
    String slug = "plat-new-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "owner@" + slug + ".test";

    ResponseEntity<Map> created =
        api.call(
            HttpMethod.POST,
            "/api/platform/tenants",
            platformToken,
            Map.of(
                "tenantName",
                "Created by platform",
                "tenantSlug",
                slug,
                "adminEmail",
                email,
                "adminPassword",
                TestTenants.PASSWORD),
            Map.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(api.login(slug, email)).isNotBlank();
  }

  // --- suspension ---

  @Test
  void suspendBlocksExistingTokensAndLoginUntilActivated() {
    TenantSession suspended = api.register("plat-susp");
    TenantSession bystander = api.register("plat-ok");

    ResponseEntity<Map> suspendResponse =
        api.call(
            HttpMethod.POST,
            "/api/platform/tenants/" + suspended.tenantId() + "/suspend",
            platformToken,
            null,
            Map.class);
    assertThat(suspendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(suspendResponse.getBody()).containsEntry("status", "SUSPENDED");

    // A token issued before the suspension stops working immediately.
    ResponseEntity<String> withOldToken =
        api.call(HttpMethod.GET, "/api/users", suspended.adminToken(), null, String.class);
    assertThat(withOldToken.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(withOldToken.getBody()).contains("Tenant is suspended");

    // Correct credentials: 403. Wrong password: still the generic 401 (suspension not revealed).
    assertThat(loginStatus(suspended, TestTenants.PASSWORD)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(loginStatus(suspended, "wrong-password")).isEqualTo(HttpStatus.UNAUTHORIZED);

    // Other tenants are unaffected.
    assertThat(
            api.call(HttpMethod.GET, "/api/users", bystander.adminToken(), null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    api.call(
        HttpMethod.POST,
        "/api/platform/tenants/" + suspended.tenantId() + "/activate",
        platformToken,
        null,
        Map.class);

    assertThat(
            api.call(HttpMethod.GET, "/api/users", suspended.adminToken(), null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(loginStatus(suspended, TestTenants.PASSWORD)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void tenantTokenCannotSuspendTenants() {
    TenantSession tenant = api.register("plat-self");

    ResponseEntity<String> response =
        api.call(
            HttpMethod.POST,
            "/api/platform/tenants/" + tenant.tenantId() + "/suspend",
            tenant.adminToken(),
            null,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM tenants WHERE id = ?::uuid", String.class, tenant.tenantId()))
        .isEqualTo("ACTIVE");
  }

  private ResponseEntity<Map> platformLogin(String email, String password) {
    return rest.postForEntity(
        "/api/platform/auth/login", Map.of("email", email, "password", password), Map.class);
  }

  private HttpStatus loginStatus(TenantSession tenant, String password) {
    return HttpStatus.valueOf(
        rest.postForEntity(
                "/api/auth/login",
                Map.of(
                    "tenantSlug",
                    tenant.slug(),
                    "email",
                    tenant.adminEmail(),
                    "password",
                    password),
                String.class)
            .getStatusCode()
            .value());
  }
}
