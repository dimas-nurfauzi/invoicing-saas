package io.github.dimasnurfauzi.invoicing.customer;

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
 * Cross-tenant isolation for customers: tenant A must not read, list, search, update, archive or
 * restore tenant B's customers, and gets the same 404 as for an id that doesn't exist. B's rows are
 * checked with JDBC (bypassing the filter) so the assertion is about the real database state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CustomerIsolationTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenantA;
  private TenantSession tenantB;
  private String customerA;
  private String customerB;

  @BeforeEach
  void twoTenantsWithCustomers() {
    api = new TestTenants(rest);
    tenantA = api.register("cust-a");
    tenantB = api.register("cust-b");
    customerA = api.createCustomer(tenantA.adminToken(), "Alpha Store");
    customerB = api.createCustomer(tenantB.adminToken(), "Beta Secret Client");
  }

  @Test
  void listAndSearchReturnOnlyOwnCustomers() {
    assertThat(idsVisibleTo(tenantA, "")).containsExactly(customerA);
    assertThat(idsVisibleTo(tenantB, "")).containsExactly(customerB);
    // Searching for B's customer by name from A finds nothing.
    assertThat(idsVisibleTo(tenantA, "Beta Secret")).isEmpty();
    assertThat(idsVisibleTo(tenantA, "&includeArchived=true")).containsExactly(customerA);
  }

  @Test
  void cannotReadAnotherTenantsCustomer() {
    ResponseEntity<String> crossTenant = get(tenantA, customerB);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertSameNotFound(crossTenant, get(tenantA, UUID.randomUUID().toString()));
    assertThat(crossTenant.getBody()).doesNotContain("Beta Secret");
  }

  @Test
  void cannotUpdateAnotherTenantsCustomer() {
    ResponseEntity<String> crossTenant = put(tenantA, customerB);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertSameNotFound(crossTenant, put(tenantA, UUID.randomUUID().toString()));
    assertThat(nameInDatabase(customerB)).isEqualTo("Beta Secret Client");
  }

  @Test
  void cannotArchiveOrRestoreAnotherTenantsCustomer() {
    ResponseEntity<String> archive =
        api.call(
            HttpMethod.DELETE,
            "/api/customers/" + customerB,
            tenantA.adminToken(),
            null,
            String.class);
    assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(archivedInDatabase(customerB)).isFalse();

    jdbc.update("UPDATE customers SET archived_at = now() WHERE id = ?::uuid", customerB);
    ResponseEntity<String> restore =
        api.call(
            HttpMethod.POST,
            "/api/customers/" + customerB + "/restore",
            tenantA.adminToken(),
            null,
            String.class);
    assertThat(restore.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(archivedInDatabase(customerB)).isTrue();
  }

  @Test
  void isolationHoldsInBothDirections() {
    assertThat(get(tenantB, customerA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(put(tenantB, customerA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(nameInDatabase(customerA)).isEqualTo("Alpha Store");
  }

  @Test
  void tenantIdInRequestBodyIsIgnored() {
    ResponseEntity<Map> response =
        api.call(
            HttpMethod.POST,
            "/api/customers",
            tenantA.adminToken(),
            Map.of(
                "name", "Planted",
                "tenantId", tenantB.tenantId(),
                "tenant_id", tenantB.tenantId()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).doesNotContainKey("tenantId");
    String id = (String) response.getBody().get("id");
    assertThat(
            jdbc.queryForObject(
                "SELECT tenant_id::text FROM customers WHERE id = ?::uuid", String.class, id))
        .isEqualTo(tenantA.tenantId());
  }

  private List<String> idsVisibleTo(TenantSession tenant, String query) {
    ResponseEntity<Map> response =
        api.call(
            HttpMethod.GET,
            query.startsWith("&")
                ? "/api/customers?size=100" + query
                : "/api/customers?size=100&q={q}",
            tenant.adminToken(),
            null,
            Map.class,
            query);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return ((List<Map<String, Object>>) response.getBody().get("content"))
        .stream().map(c -> (String) c.get("id")).toList();
  }

  private ResponseEntity<String> get(TenantSession as, String id) {
    return api.call(HttpMethod.GET, "/api/customers/" + id, as.adminToken(), null, String.class);
  }

  private ResponseEntity<String> put(TenantSession as, String id) {
    return api.call(
        HttpMethod.PUT,
        "/api/customers/" + id,
        as.adminToken(),
        Map.of("name", "Hijacked"),
        String.class);
  }

  private static void assertSameNotFound(
      ResponseEntity<String> crossTenant, ResponseEntity<String> nonexistent) {
    assertThat(nonexistent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(withoutInstance(crossTenant.getBody()))
        .isEqualTo(withoutInstance(nonexistent.getBody()));
  }

  private static String withoutInstance(String problemJson) {
    return problemJson.replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  private String nameInDatabase(String id) {
    return jdbc.queryForObject("SELECT name FROM customers WHERE id = ?::uuid", String.class, id);
  }

  private boolean archivedInDatabase(String id) {
    return jdbc.queryForObject(
        "SELECT archived_at IS NOT NULL FROM customers WHERE id = ?::uuid", Boolean.class, id);
  }
}
