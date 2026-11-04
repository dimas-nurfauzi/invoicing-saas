package io.github.dimasnurfauzi.invoicing.customer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.HashMap;
import java.util.List;
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

/** Customer CRUD within one tenant: normalization, archive/restore, paging, search, roles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CustomerTests {

  @Autowired TestRestTemplate rest;

  private TestTenants api;
  private TenantSession tenant;
  private String token;

  @BeforeEach
  void freshTenant() {
    api = new TestTenants(rest);
    tenant = api.register("cust");
    token = tenant.adminToken();
  }

  @Test
  void createNormalizesInput() {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "  Toko Maju  ");
    body.put("email", "Billing@TokoMaju.ID");
    body.put("phone", "");
    body.put("billingAddress", "Jl. Merdeka 1, Bandung");
    body.put("taxId", "   ");

    ResponseEntity<Map> created =
        api.call(HttpMethod.POST, "/api/customers", token, body, Map.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody())
        .containsEntry("name", "Toko Maju")
        .containsEntry("email", "billing@tokomaju.id")
        .containsEntry("phone", null)
        .containsEntry("taxId", null)
        .containsEntry("archived", false)
        .doesNotContainKey("tenantId");
  }

  @Test
  void updateReplacesFields() {
    String id = api.createCustomer(token, "Old Name");

    ResponseEntity<Map> updated =
        api.call(
            HttpMethod.PUT,
            "/api/customers/" + id,
            token,
            Map.of("name", "New Name", "phone", "+62 812-3456-7890"),
            Map.class);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody())
        .containsEntry("name", "New Name")
        .containsEntry("phone", "+62 812-3456-7890");
    assertThat(get(id).getBody()).containsEntry("name", "New Name");
  }

  @Test
  void archiveHidesFromListUntilRestored() {
    String id = api.createCustomer(token, "Archive Me");

    assertThat(
            api.call(HttpMethod.DELETE, "/api/customers/" + id, token, null, Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(ids("/api/customers")).doesNotContain(id);
    assertThat(ids("/api/customers?includeArchived=true")).contains(id);
    assertThat(get(id).getBody()).containsEntry("archived", true);
    assertThat(
            api.call(
                    HttpMethod.PUT,
                    "/api/customers/" + id,
                    token,
                    Map.of("name", "X"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    ResponseEntity<Map> restored =
        api.call(HttpMethod.POST, "/api/customers/" + id + "/restore", token, null, Map.class);
    assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(restored.getBody()).containsEntry("archived", false);
    assertThat(ids("/api/customers")).contains(id);
  }

  @Test
  void pagingIsStableAndBounded() {
    api.createCustomer(token, "C Customer");
    api.createCustomer(token, "a Customer");
    api.createCustomer(token, "B Customer");

    ResponseEntity<Map> page0 =
        api.call(HttpMethod.GET, "/api/customers?size=2&page=0", token, null, Map.class);

    assertThat(page0.getBody())
        .containsEntry("page", 0)
        .containsEntry("size", 2)
        .containsEntry("totalElements", 3)
        .containsEntry("totalPages", 2);
    // Sorted by name, case-insensitive.
    assertThat(names(page0)).containsExactly("a Customer", "B Customer");
    assertThat(
            names(api.call(HttpMethod.GET, "/api/customers?size=2&page=1", token, null, Map.class)))
        .containsExactly("C Customer");

    for (String bad : new String[] {"size=0", "size=101", "page=-1"}) {
      assertThat(
              api.call(HttpMethod.GET, "/api/customers?" + bad, token, null, String.class)
                  .getStatusCode())
          .as(bad)
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void searchMatchesNameOrEmailAndTreatsWildcardsLiterally() {
    api.createCustomer(token, "100% Cotton");
    api.createCustomer(token, "Other Shop");
    api.call(
        HttpMethod.POST,
        "/api/customers",
        token,
        Map.of("name", "Mail Only", "email", "finance@acme.test"),
        Map.class);

    assertThat(names(search("cotton"))).containsExactly("100% Cotton");
    assertThat(names(search("ACME"))).containsExactly("Mail Only");
    // "%" must not act as a wildcard.
    assertThat(names(search("%"))).containsExactly("100% Cotton");
    assertThat(names(search("_"))).isEmpty();
  }

  @Test
  void staffCanEditButNotArchive() {
    String staffEmail = "staff@" + tenant.slug() + ".test";
    api.createUser(token, staffEmail, "STAFF");
    String staffToken = api.login(tenant.slug(), staffEmail);
    String id = api.createCustomer(staffToken, "Staff Created");

    assertThat(
            api.call(
                    HttpMethod.PUT,
                    "/api/customers/" + id,
                    staffToken,
                    Map.of("name", "Staff Edited"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            api.call(HttpMethod.DELETE, "/api/customers/" + id, staffToken, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            api.call(
                    HttpMethod.POST,
                    "/api/customers/" + id + "/restore",
                    staffToken,
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void invalidInputIsBadRequest() {
    for (Map<String, Object> body :
        List.<Map<String, Object>>of(
            Map.of("name", " "),
            Map.of("name", "X", "email", "not-an-email"),
            Map.of("name", "X", "phone", "call me maybe"),
            Map.of("name", "x".repeat(201)))) {
      assertThat(
              api.call(HttpMethod.POST, "/api/customers", token, body, String.class)
                  .getStatusCode())
          .as(body.toString())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  private ResponseEntity<Map> get(String id) {
    return api.call(HttpMethod.GET, "/api/customers/" + id, token, null, Map.class);
  }

  private ResponseEntity<Map> search(String q) {
    return api.call(HttpMethod.GET, "/api/customers?q={q}", token, null, Map.class, q);
  }

  private List<String> ids(String path) {
    return content(api.call(HttpMethod.GET, path, token, null, Map.class)).stream()
        .map(c -> (String) c.get("id"))
        .toList();
  }

  private static List<String> names(ResponseEntity<Map> response) {
    return content(response).stream().map(c -> (String) c.get("name")).toList();
  }

  private static List<Map<String, Object>> content(ResponseEntity<Map> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (List<Map<String, Object>>) response.getBody().get("content");
  }
}
