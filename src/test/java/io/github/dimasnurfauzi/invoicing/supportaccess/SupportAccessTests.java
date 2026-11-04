package io.github.dimasnurfauzi.invoicing.supportaccess;

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
 * Support access end to end: tenant-granted, time-limited, revocable, reason required, every read
 * logged and visible to the tenant, and isolated between tenants.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SupportAccessTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenantA;
  private TenantSession tenantB;
  private String platformToken;

  @BeforeEach
  void setUp() {
    api = new TestTenants(rest);
    tenantA = api.register("sup-a");
    tenantB = api.register("sup-b");
    ResponseEntity<Map> login =
        rest.postForEntity(
            "/api/platform/auth/login",
            Map.of("email", "platform-admin@test.local", "password", "platform-test-password"),
            Map.class);
    platformToken = (String) login.getBody().get("accessToken");
  }

  // --- the platform read path ---

  @Test
  void withoutGrantSupportGets403AndNothingIsLogged() {
    ResponseEntity<String> response = supportUsers(tenantA, "TICKET-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).doesNotContain(tenantA.adminEmail());
    assertThat(logCount(tenantA)).isZero();
  }

  @Test
  void grantAllowsReadingThatTenantOnly() {
    grant(tenantA, 24);

    ResponseEntity<List> users =
        api.call(
            HttpMethod.GET,
            "/api/platform/tenants/" + tenantA.tenantId() + "/support/users?reason=TICKET-2",
            platformToken,
            null,
            List.class);

    assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map<String, Object>> body = users.getBody();
    assertThat(body).extracting(u -> u.get("id")).containsExactly(tenantA.adminId());
    assertThat(body.get(0)).doesNotContainKey("passwordHash");
    // A's grant says nothing about B.
    assertThat(supportUsers(tenantB, "TICKET-2").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void everyReadIsLoggedAndVisibleToTheTenantOnly() {
    grant(tenantA, 1);
    supportUsers(tenantA, "TICKET-3 invoice total mismatch");

    ResponseEntity<List> logs =
        api.call(
            HttpMethod.GET, "/api/support-access/logs", tenantA.adminToken(), null, List.class);

    assertThat(logs.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map<String, Object>> entries = logs.getBody();
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0))
        .containsEntry("reason", "TICKET-3 invoice total mismatch")
        .containsEntry("resource", "GET users")
        .containsEntry("platformAdminId", platformAdminId());
    assertThat(
            api.call(
                    HttpMethod.GET,
                    "/api/support-access/logs",
                    tenantB.adminToken(),
                    null,
                    List.class)
                .getBody())
        .isEmpty();
  }

  @Test
  void revokedGrantStopsAccess() {
    String grantId = grant(tenantA, 24);
    assertThat(supportUsers(tenantA, "TICKET-4").getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(
            api.call(
                    HttpMethod.DELETE,
                    "/api/support-access/" + grantId,
                    tenantA.adminToken(),
                    null,
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(supportUsers(tenantA, "TICKET-4").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void expiredGrantStopsAccess() {
    String grantId = grant(tenantA, 1);
    jdbc.update(
        "UPDATE support_access_grants SET created_at = now() - interval '3 hours',"
            + " expires_at = now() - interval '2 hours' WHERE id = ?::uuid",
        grantId);

    assertThat(supportUsers(tenantA, "TICKET-5").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void reasonIsRequired() {
    grant(tenantA, 1);

    assertThat(
            api.call(
                    HttpMethod.GET,
                    "/api/platform/tenants/" + tenantA.tenantId() + "/support/users",
                    platformToken,
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(supportUsers(tenantA, "ab").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(logCount(tenantA)).isZero();
  }

  @Test
  void customersSupportViewNeedsGrantAndIsLogged() {
    String customerId = api.createCustomer(tenantA.adminToken(), "Support Customer");

    ResponseEntity<String> denied =
        api.call(
            HttpMethod.GET,
            "/api/platform/tenants/" + tenantA.tenantId() + "/support/customers?reason=TICKET-9",
            platformToken,
            null,
            String.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    grant(tenantA, 1);
    ResponseEntity<Map> allowed =
        api.call(
            HttpMethod.GET,
            "/api/platform/tenants/" + tenantA.tenantId() + "/support/customers?reason=TICKET-9",
            platformToken,
            null,
            Map.class);

    assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Map<String, Object>>) allowed.getBody().get("content"))
        .extracting(c -> c.get("id"))
        .containsExactly(customerId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM support_access_logs WHERE tenant_id = ?::uuid"
                    + " AND resource = 'GET customers'",
                Integer.class,
                tenantA.tenantId()))
        .isOne();
  }

  @Test
  void unknownTenantIs404() {
    ResponseEntity<String> response =
        api.call(
            HttpMethod.GET,
            "/api/platform/tenants/" + UUID.randomUUID() + "/support/users?reason=TICKET-6",
            platformToken,
            null,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // --- who may do what ---

  @Test
  void onlyTenantAdminManagesGrants() {
    String staffEmail = "staff@" + tenantA.slug() + ".test";
    api.createUser(tenantA.adminToken(), staffEmail, "STAFF");
    String staffToken = api.login(tenantA.slug(), staffEmail);

    assertThat(
            api.call(
                    HttpMethod.POST,
                    "/api/support-access",
                    staffToken,
                    Map.of("durationHours", 24),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            api.call(HttpMethod.GET, "/api/support-access/logs", staffToken, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void durationMustBeBetween1And72Hours() {
    for (int hours : new int[] {0, 73}) {
      assertThat(
              api.call(
                      HttpMethod.POST,
                      "/api/support-access",
                      tenantA.adminToken(),
                      Map.of("durationHours", hours),
                      String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void tokensCannotCrossBetweenTenantAndPlatformApis() {
    grant(tenantA, 1);

    assertThat(
            api.call(
                    HttpMethod.GET,
                    "/api/platform/tenants/"
                        + tenantA.tenantId()
                        + "/support/users?reason=TICKET-7",
                    tenantA.adminToken(),
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            api.call(HttpMethod.GET, "/api/support-access", platformToken, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- isolation of the new tenant-owned tables ---

  @Test
  void tenantCannotSeeOrRevokeAnotherTenantsGrant() {
    String grantB = grant(tenantB, 24);

    ResponseEntity<List> listA =
        api.call(HttpMethod.GET, "/api/support-access", tenantA.adminToken(), null, List.class);
    assertThat(((List<Map<String, Object>>) listA.getBody()))
        .extracting(g -> g.get("id"))
        .doesNotContain(grantB);

    ResponseEntity<String> revoke =
        api.call(
            HttpMethod.DELETE,
            "/api/support-access/" + grantB,
            tenantA.adminToken(),
            null,
            String.class);
    assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NULL FROM support_access_grants WHERE id = ?::uuid",
                Boolean.class,
                grantB))
        .isTrue();
    assertThat(supportUsers(tenantB, "TICKET-8").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private String grant(TenantSession tenant, int hours) {
    ResponseEntity<Map> response =
        api.call(
            HttpMethod.POST,
            "/api/support-access",
            tenant.adminToken(),
            Map.of("durationHours", hours),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("active", true);
    return (String) response.getBody().get("id");
  }

  private ResponseEntity<String> supportUsers(TenantSession tenant, String reason) {
    return api.call(
        HttpMethod.GET,
        "/api/platform/tenants/" + tenant.tenantId() + "/support/users?reason={reason}",
        platformToken,
        null,
        String.class,
        reason);
  }

  private int logCount(TenantSession tenant) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM support_access_logs WHERE tenant_id = ?::uuid",
        Integer.class,
        tenant.tenantId());
  }

  private String platformAdminId() {
    return jdbc.queryForObject(
        "SELECT id::text FROM platform_admins WHERE email = 'platform-admin@test.local'",
        String.class);
  }
}
