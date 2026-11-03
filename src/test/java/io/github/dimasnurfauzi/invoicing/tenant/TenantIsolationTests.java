package io.github.dimasnurfauzi.invoicing.tenant;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cross-tenant isolation for the first tenant-owned resource ({@code users}). Tenant A, holding a
 * valid ADMIN token, must not be able to read, list, update or delete tenant B's data, and must get
 * the same 404 it would get for an id that doesn't exist at all.
 *
 * <p>Assertions about B's data use JDBC directly (bypassing the tenant filter) so they check what
 * is really in the database, not what the filtered API is willing to show.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantIsolationTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenantA;
  private TenantSession tenantB;
  private String staffA;
  private String staffB;

  @BeforeEach
  void twoTenants() {
    api = new TestTenants(rest);
    tenantA = api.register("tenant-a");
    tenantB = api.register("tenant-b");
    staffA = api.createUser(tenantA.adminToken(), "staff@" + tenantA.slug() + ".test", "STAFF");
    staffB = api.createUser(tenantB.adminToken(), "staff@" + tenantB.slug() + ".test", "STAFF");
  }

  @Test
  void listReturnsOnlyOwnTenantsUsers() {
    assertThat(userIdsVisibleTo(tenantA)).containsExactlyInAnyOrder(tenantA.adminId(), staffA);
    assertThat(userIdsVisibleTo(tenantB)).containsExactlyInAnyOrder(tenantB.adminId(), staffB);
  }

  @Test
  void cannotReadAnotherTenantsUser() {
    ResponseEntity<String> crossTenant = get(tenantA, staffB);
    ResponseEntity<String> nonexistent = get(tenantA, UUID.randomUUID().toString());

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertSameNotFound(crossTenant, nonexistent);
    assertThat(crossTenant.getBody()).doesNotContain(tenantB.slug());
  }

  @Test
  void cannotUpdateAnotherTenantsUser() {
    ResponseEntity<String> crossTenant =
        api.call(
            HttpMethod.PATCH,
            "/api/users/" + staffB,
            tenantA.adminToken(),
            Map.of("role", "ADMIN"),
            String.class);
    ResponseEntity<String> nonexistent =
        api.call(
            HttpMethod.PATCH,
            "/api/users/" + UUID.randomUUID(),
            tenantA.adminToken(),
            Map.of("role", "ADMIN"),
            String.class);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertSameNotFound(crossTenant, nonexistent);
    assertThat(roleInDatabase(staffB)).isEqualTo("STAFF");
  }

  @Test
  void cannotDeleteAnotherTenantsUser() {
    ResponseEntity<String> crossTenant =
        api.call(
            HttpMethod.DELETE, "/api/users/" + staffB, tenantA.adminToken(), null, String.class);
    ResponseEntity<String> nonexistent =
        api.call(
            HttpMethod.DELETE,
            "/api/users/" + UUID.randomUUID(),
            tenantA.adminToken(),
            null,
            String.class);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertSameNotFound(crossTenant, nonexistent);
    assertThat(existsInDatabase(staffB)).isTrue();
  }

  @Test
  void isolationHoldsInBothDirections() {
    assertThat(get(tenantB, staffA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(tenantB, tenantA.adminId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            api.call(
                    HttpMethod.DELETE,
                    "/api/users/" + tenantA.adminId(),
                    tenantB.adminToken(),
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(existsInDatabase(tenantA.adminId())).isTrue();
  }

  @Test
  void tenantIdInRequestBodyIsIgnored() {
    // A tries to plant a user in B by sending B's tenant id. The field isn't part of the API;
    // the user must land in A (the token's tenant).
    ResponseEntity<Map> response =
        api.call(
            HttpMethod.POST,
            "/api/users",
            tenantA.adminToken(),
            Map.of(
                "email",
                "planted@" + tenantA.slug() + ".test",
                "password",
                TestTenants.PASSWORD,
                "role",
                "STAFF",
                "tenantId",
                tenantB.tenantId(),
                "tenant_id",
                tenantB.tenantId()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String plantedId = (String) response.getBody().get("id");
    assertThat(tenantInDatabase(plantedId)).isEqualTo(tenantA.tenantId());
    assertThat(userIdsVisibleTo(tenantB)).doesNotContain(plantedId);
  }

  @Test
  void sameEmailInTwoTenantsLogsIntoOwnTenantOnly() {
    String email = "shared-" + UUID.randomUUID() + "@example.test";
    String inA = api.createUser(tenantA.adminToken(), email, "STAFF");
    String inB = api.createUser(tenantB.adminToken(), email, "STAFF");

    String tokenB = api.login(tenantB.slug(), email);
    ResponseEntity<Map> me = api.call(HttpMethod.GET, "/api/me", tokenB, null, Map.class);

    assertThat(me.getBody())
        .containsEntry("userId", inB)
        .containsEntry("tenantId", tenantB.tenantId());
    assertThat(get(tenantB, inA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private List<String> userIdsVisibleTo(TenantSession tenant) {
    ResponseEntity<List> response =
        api.call(HttpMethod.GET, "/api/users", tenant.adminToken(), null, List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return ((List<Map<String, Object>>) response.getBody())
        .stream().map(user -> (String) user.get("id")).toList();
  }

  private ResponseEntity<String> get(TenantSession as, String userId) {
    return api.call(HttpMethod.GET, "/api/users/" + userId, as.adminToken(), null, String.class);
  }

  /** Cross-tenant must be indistinguishable from "doesn't exist" (apart from the request path). */
  private static void assertSameNotFound(
      ResponseEntity<String> crossTenant, ResponseEntity<String> nonexistent) {
    assertThat(nonexistent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(withoutInstance(crossTenant.getBody()))
        .isEqualTo(withoutInstance(nonexistent.getBody()));
  }

  private static String withoutInstance(String problemJson) {
    return problemJson.replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  private String roleInDatabase(String userId) {
    return jdbc.queryForObject("SELECT role FROM users WHERE id = ?::uuid", String.class, userId);
  }

  private String tenantInDatabase(String userId) {
    return jdbc.queryForObject(
        "SELECT tenant_id::text FROM users WHERE id = ?::uuid", String.class, userId);
  }

  private boolean existsInDatabase(String userId) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE id = ?::uuid", Integer.class, userId)
        == 1;
  }
}
