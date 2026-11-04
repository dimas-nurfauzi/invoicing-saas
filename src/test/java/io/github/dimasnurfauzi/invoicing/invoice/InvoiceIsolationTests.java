package io.github.dimasnurfauzi.invoicing.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.HashMap;
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
 * Cross-tenant isolation for invoices and their lines, including the customer reference: tenant A
 * can't read, list, update or delete B's invoices, and can't attach B's customer to an invoice. B's
 * rows are checked with JDBC (bypassing the filter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceIsolationTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenantA;
  private TenantSession tenantB;
  private String customerA;
  private String customerB;
  private String invoiceA;
  private String invoiceB;

  @BeforeEach
  void twoTenantsWithInvoices() {
    api = new TestTenants(rest);
    tenantA = api.register("inv-a");
    tenantB = api.register("inv-b");
    customerA = api.createCustomer(tenantA.adminToken(), "Alpha Buyer");
    customerB = api.createCustomer(tenantB.adminToken(), "Beta Secret Buyer");
    invoiceA = api.createInvoice(tenantA.adminToken(), customerA);
    invoiceB = api.createInvoice(tenantB.adminToken(), customerB);
  }

  @Test
  void listReturnsOnlyOwnInvoices() {
    assertThat(ids(tenantA, "/api/invoices")).containsExactly(invoiceA);
    assertThat(ids(tenantB, "/api/invoices")).containsExactly(invoiceB);
    // Filtering by B's customer from A finds nothing.
    assertThat(ids(tenantA, "/api/invoices?customerId=" + customerB)).isEmpty();
  }

  @Test
  void cannotReadAnotherTenantsInvoice() {
    ResponseEntity<String> crossTenant = get(tenantA, invoiceB);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertSameNotFound(crossTenant, get(tenantA, UUID.randomUUID().toString()));
    assertThat(crossTenant.getBody()).doesNotContain("Beta Secret").doesNotContain("Jasa desain");
  }

  @Test
  void cannotUpdateAnotherTenantsInvoice() {
    ResponseEntity<String> crossTenant =
        api.call(
            HttpMethod.PUT,
            "/api/invoices/" + invoiceB,
            tenantA.adminToken(),
            TestTenants.invoiceBody(customerA),
            String.class);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(customerOf(invoiceB)).isEqualTo(customerB);
    assertThat(tenantOf(invoiceB)).isEqualTo(tenantB.tenantId());
  }

  @Test
  void cannotDeleteAnotherTenantsInvoice() {
    ResponseEntity<String> crossTenant =
        api.call(
            HttpMethod.DELETE,
            "/api/invoices/" + invoiceB,
            tenantA.adminToken(),
            null,
            String.class);

    assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoices WHERE id = ?::uuid", Integer.class, invoiceB))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoice_lines WHERE invoice_id = ?::uuid",
                Integer.class,
                invoiceB))
        .isOne();
  }

  @Test
  void cannotInvoiceAnotherTenantsCustomer() {
    ResponseEntity<String> create =
        api.call(
            HttpMethod.POST,
            "/api/invoices",
            tenantA.adminToken(),
            TestTenants.invoiceBody(customerB),
            String.class);

    assertThat(create.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(create.getBody()).contains("Customer not found").doesNotContain("Beta Secret");
    assertThat(invoiceCount(tenantA)).isOne();
    assertThat(invoiceCount(tenantB)).isOne();

    // Nor switch an own invoice over to B's customer.
    ResponseEntity<String> update =
        api.call(
            HttpMethod.PUT,
            "/api/invoices/" + invoiceA,
            tenantA.adminToken(),
            TestTenants.invoiceBody(customerB),
            String.class);
    assertThat(update.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(customerOf(invoiceA)).isEqualTo(customerA);
  }

  @Test
  void isolationHoldsInBothDirections() {
    assertThat(get(tenantB, invoiceA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ids(tenantB, "/api/invoices?customerId=" + customerA)).isEmpty();
  }

  @Test
  void tenantIdInRequestBodyIsIgnored() {
    Map<String, Object> body = new HashMap<>(TestTenants.invoiceBody(customerA));
    body.put("tenantId", tenantB.tenantId());
    body.put("tenant_id", tenantB.tenantId());

    ResponseEntity<Map> created =
        api.call(HttpMethod.POST, "/api/invoices", tenantA.adminToken(), body, Map.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = (String) created.getBody().get("id");
    assertThat(tenantOf(id)).isEqualTo(tenantA.tenantId());
    assertThat(
            jdbc.queryForObject(
                "SELECT DISTINCT tenant_id::text FROM invoice_lines WHERE invoice_id = ?::uuid",
                String.class,
                id))
        .isEqualTo(tenantA.tenantId());
  }

  private List<String> ids(TenantSession tenant, String path) {
    ResponseEntity<Map> response =
        api.call(HttpMethod.GET, path, tenant.adminToken(), null, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return ((List<Map<String, Object>>) response.getBody().get("content"))
        .stream().map(i -> (String) i.get("id")).toList();
  }

  private ResponseEntity<String> get(TenantSession as, String id) {
    return api.call(HttpMethod.GET, "/api/invoices/" + id, as.adminToken(), null, String.class);
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

  private String customerOf(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT customer_id::text FROM invoices WHERE id = ?::uuid", String.class, invoiceId);
  }

  private String tenantOf(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT tenant_id::text FROM invoices WHERE id = ?::uuid", String.class, invoiceId);
  }

  private int invoiceCount(TenantSession tenant) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM invoices WHERE tenant_id = ?::uuid",
        Integer.class,
        tenant.tenantId());
  }
}
