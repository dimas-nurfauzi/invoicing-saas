package io.github.dimasnurfauzi.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Tenant A can't record or read payments on tenant B's invoices (404), nor plant one in B. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentIsolationTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenantA;
  private TenantSession tenantB;
  private String invoiceA;
  private String invoiceB;

  @BeforeEach
  void twoTenantsWithIssuedInvoices() {
    api = new TestTenants(rest);
    tenantA = api.register("pay-a");
    tenantB = api.register("pay-b");
    invoiceA =
        api.createIssuedInvoice(
            tenantA.adminToken(), api.createCustomer(tenantA.adminToken(), "Alpha Payer"));
    invoiceB =
        api.createIssuedInvoice(
            tenantB.adminToken(), api.createCustomer(tenantB.adminToken(), "Beta Payer"));
    api.pay(tenantB.adminToken(), invoiceB, "1000");
  }

  @Test
  void cannotRecordPaymentOnAnotherTenantsInvoice() {
    ResponseEntity<Map> response = api.pay(tenantA.adminToken(), invoiceB, "110000");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(amountPaid(invoiceB)).isEqualTo("1000.00");
    assertThat(paymentCount(invoiceB)).isOne();
  }

  @Test
  void cannotListAnotherTenantsPayments() {
    ResponseEntity<String> response =
        api.call(
            HttpMethod.GET,
            "/api/invoices/" + invoiceB + "/payments",
            tenantA.adminToken(),
            null,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("1000.00");
  }

  @Test
  void ownPaymentsLandInOwnTenant() {
    api.pay(tenantA.adminToken(), invoiceA, "500");

    ResponseEntity<List> own =
        api.call(
            HttpMethod.GET,
            "/api/invoices/" + invoiceA + "/payments",
            tenantA.adminToken(),
            null,
            List.class);

    assertThat(own.getBody()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT tenant_id::text FROM payments WHERE invoice_id = ?::uuid",
                String.class,
                invoiceA))
        .isEqualTo(tenantA.tenantId());
  }

  private String amountPaid(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT amount_paid::text FROM invoices WHERE id = ?::uuid", String.class, invoiceId);
  }

  private int paymentCount(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM payments WHERE invoice_id = ?::uuid", Integer.class, invoiceId);
  }
}
