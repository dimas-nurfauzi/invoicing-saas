package io.github.dimasnurfauzi.invoicing.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Registers tenants and calls the API as their users, through the real HTTP endpoints. */
public final class TestTenants {

  public static final String PASSWORD = "correct-horse-battery";

  private final TestRestTemplate rest;

  public TestTenants(TestRestTemplate rest) {
    this.rest = rest;
  }

  /** A registered tenant, logged in as its first ADMIN. */
  public record TenantSession(
      String slug, String tenantId, String adminId, String adminEmail, String adminToken) {}

  public TenantSession register(String label) {
    String slug = label + "-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".test";
    ResponseEntity<Map> response =
        rest.postForEntity(
            "/api/tenants",
            Map.of(
                "tenantName", label,
                "tenantSlug", slug,
                "adminEmail", email,
                "adminPassword", PASSWORD),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return new TenantSession(
        slug,
        (String) response.getBody().get("tenantId"),
        (String) response.getBody().get("userId"),
        email,
        login(slug, email));
  }

  public String login(String slug, String email) {
    ResponseEntity<Map> response =
        rest.postForEntity(
            "/api/auth/login",
            Map.of("tenantSlug", slug, "email", email, "password", PASSWORD),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (String) response.getBody().get("accessToken");
  }

  /** Creates a user in the token's tenant and returns its id. */
  public String createUser(String token, String email, String role) {
    ResponseEntity<Map> response =
        call(
            HttpMethod.POST,
            "/api/users",
            token,
            Map.of("email", email, "password", PASSWORD, "role", role),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
  }

  /** Creates a customer in the token's tenant and returns its id. */
  public String createCustomer(String token, String name) {
    ResponseEntity<Map> response =
        call(HttpMethod.POST, "/api/customers", token, Map.of("name", name), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
  }

  /** A one-line draft invoice body: 1 × 100000.00 with 11% tax (total 111000.00). */
  public static Map<String, Object> invoiceBody(String customerId) {
    return Map.of(
        "customerId",
        customerId,
        "issueDate",
        "2026-11-01",
        "dueDate",
        "2026-11-30",
        "lines",
        List.of(
            Map.of(
                "description", "Jasa desain",
                "quantity", "1",
                "unitPrice", "100000",
                "taxRatePercent", "11")));
  }

  /** Creates a draft invoice in the token's tenant and returns its id. */
  public String createInvoice(String token, String customerId) {
    ResponseEntity<Map> response =
        call(HttpMethod.POST, "/api/invoices", token, invoiceBody(customerId), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
  }

  /** {@code uriVariables} fill {@code {placeholders}} in {@code path} (URL-encoded). */
  public <T> ResponseEntity<T> call(
      HttpMethod method,
      String path,
      String token,
      Object body,
      Class<T> type,
      Object... uriVariables) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return rest.exchange(path, method, new HttpEntity<>(body, headers), type, uriVariables);
  }
}
