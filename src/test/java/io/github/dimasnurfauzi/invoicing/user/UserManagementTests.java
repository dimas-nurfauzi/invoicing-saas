package io.github.dimasnurfauzi.invoicing.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** User management inside one tenant: roles, self-protection, validation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserManagementTests {

  @Autowired TestRestTemplate rest;

  private TestTenants api;
  private TenantSession tenant;
  private String staffId;
  private String staffToken;

  @BeforeEach
  void tenantWithStaff() {
    api = new TestTenants(rest);
    tenant = api.register("users");
    String staffEmail = "staff@" + tenant.slug() + ".test";
    staffId = api.createUser(tenant.adminToken(), staffEmail, "STAFF");
    staffToken = api.login(tenant.slug(), staffEmail);
  }

  @Test
  void adminCreatesUserWithoutExposingHash() {
    ResponseEntity<String> response =
        api.call(
            HttpMethod.POST,
            "/api/users",
            tenant.adminToken(),
            Map.of(
                "email",
                "New@" + tenant.slug() + ".test",
                "password",
                TestTenants.PASSWORD,
                "role",
                "STAFF"),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .contains("\"email\":\"new@" + tenant.slug() + ".test\"")
        .doesNotContainIgnoringCase("password")
        .doesNotContain("$2")
        .doesNotContain("tenant");
  }

  @Test
  void adminChangesRoleAndDeletesUser() {
    ResponseEntity<Map> patched =
        api.call(
            HttpMethod.PATCH,
            "/api/users/" + staffId,
            tenant.adminToken(),
            Map.of("role", "ADMIN"),
            Map.class);
    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(patched.getBody()).containsEntry("role", "ADMIN");

    assertThat(
            api.call(
                    HttpMethod.DELETE,
                    "/api/users/" + staffId,
                    tenant.adminToken(),
                    null,
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            api.call(HttpMethod.GET, "/api/users/" + staffId, tenant.adminToken(), null, Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void staffCanReadButNotChange() {
    assertThat(
            api.call(HttpMethod.GET, "/api/users", staffToken, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            api.call(
                    HttpMethod.POST,
                    "/api/users",
                    staffToken,
                    Map.of(
                        "email",
                        "x@" + tenant.slug() + ".test",
                        "password",
                        TestTenants.PASSWORD,
                        "role",
                        "ADMIN"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            api.call(
                    HttpMethod.PATCH,
                    "/api/users/" + tenant.adminId(),
                    staffToken,
                    Map.of("role", "STAFF"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            api.call(
                    HttpMethod.DELETE,
                    "/api/users/" + tenant.adminId(),
                    staffToken,
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void adminCannotDemoteOrDeleteSelf() {
    assertThat(
            api.call(
                    HttpMethod.PATCH,
                    "/api/users/" + tenant.adminId(),
                    tenant.adminToken(),
                    Map.of("role", "STAFF"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            api.call(
                    HttpMethod.DELETE,
                    "/api/users/" + tenant.adminId(),
                    tenant.adminToken(),
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void duplicateEmailInSameTenantIsConflict() {
    ResponseEntity<String> response =
        api.call(
            HttpMethod.POST,
            "/api/users",
            tenant.adminToken(),
            Map.of("email", tenant.adminEmail(), "password", TestTenants.PASSWORD, "role", "STAFF"),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void invalidRoleOrPasswordIsBadRequest() {
    assertThat(
            api.call(
                    HttpMethod.POST,
                    "/api/users",
                    tenant.adminToken(),
                    Map.of(
                        "email",
                        "y@" + tenant.slug() + ".test",
                        "password",
                        "short",
                        "role",
                        "STAFF"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            api.call(
                    HttpMethod.PATCH,
                    "/api/users/" + staffId,
                    tenant.adminToken(),
                    Map.of("role", "OWNER"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
