package io.github.dimasnurfauzi.invoicing.invoice.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The overdue job, called directly with a fixed "today" (its schedule is disabled in tests). Due
 * dates are in 2001 so other test classes' invoices (due in 2026/2027) are never affected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OverdueInvoiceJobTests {

  private static final LocalDate TODAY = LocalDate.of(2001, 6, 15);

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Autowired OverdueInvoiceJob job;

  private TestTenants api;
  private TenantSession tenantA;
  private TenantSession tenantB;
  private String customerA;
  private String customerB;

  @BeforeEach
  void twoTenants() {
    api = new TestTenants(rest);
    tenantA = api.register("overdue-a");
    tenantB = api.register("overdue-b");
    customerA = api.createCustomer(tenantA.adminToken(), "Late Payer A");
    customerB = api.createCustomer(tenantB.adminToken(), "Late Payer B");
  }

  @Test
  void marksOnlySentInvoicesPastTheirDueDateInEveryTenant() {
    String pastDue = issued(tenantA, customerA, "2001-06-14");
    String dueToday = issued(tenantA, customerA, "2001-06-15");
    String notYetDue = issued(tenantA, customerA, "2001-07-01");
    String paidPastDue = issued(tenantA, customerA, "2001-06-01");
    api.pay(tenantA.adminToken(), paidPastDue, "1000");
    String voidedPastDue = issued(tenantA, customerA, "2001-06-01");
    api.call(
        HttpMethod.POST,
        "/api/invoices/" + voidedPastDue + "/void",
        tenantA.adminToken(),
        Map.of("reason", "cancelled"),
        Map.class);
    String draftPastDue = draft(tenantA, customerA, "2001-06-01");
    String otherTenantPastDue = issued(tenantB, customerB, "2001-06-10");

    job.markOverdue(TODAY);

    assertThat(status(pastDue)).isEqualTo("OVERDUE");
    assertThat(status(dueToday)).isEqualTo("SENT");
    assertThat(status(notYetDue)).isEqualTo("SENT");
    assertThat(status(paidPastDue)).isEqualTo("PAID");
    assertThat(status(voidedPastDue)).isEqualTo("VOID");
    assertThat(status(draftPastDue)).isEqualTo("DRAFT");
    // Runs per tenant via callAs: tenant B is processed too, and stays in tenant B.
    assertThat(status(otherTenantPastDue)).isEqualTo("OVERDUE");
    assertThat(
            jdbc.queryForObject(
                "SELECT tenant_id::text FROM invoices WHERE id = ?::uuid",
                String.class,
                otherTenantPastDue))
        .isEqualTo(tenantB.tenantId());
  }

  @Test
  void isIdempotent() {
    String pastDue = issued(tenantA, customerA, "2001-06-01");

    job.markOverdue(TODAY);
    String afterFirst = updatedAt(pastDue);
    job.markOverdue(TODAY);

    assertThat(status(pastDue)).isEqualTo("OVERDUE");
    assertThat(updatedAt(pastDue)).isEqualTo(afterFirst);
  }

  @Test
  void overdueInvoicesCanStillBePaid() {
    String pastDue = issued(tenantA, customerA, "2001-06-01");
    job.markOverdue(TODAY);

    assertThat(api.pay(tenantA.adminToken(), pastDue, "1000").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(status(pastDue)).isEqualTo("PAID");
  }

  @Test
  void handlesMoreThanOneBatch() {
    String customer = api.createCustomer(tenantA.adminToken(), "Bulk");
    int count = OverdueInvoiceJob.BATCH_SIZE + 3;
    for (int i = 0; i < count; i++) {
      issued(tenantA, customer, "2001-06-01");
    }

    job.markOverdue(TODAY);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoices WHERE customer_id = ?::uuid AND status = 'OVERDUE'",
                Integer.class,
                customer))
        .isEqualTo(count);
  }

  private String issued(TenantSession tenant, String customerId, String dueDate) {
    return api.createIssuedInvoice(
        tenant.adminToken(), customerId, "2001-05-01", dueDate, "IDR", "1000");
  }

  private String draft(TenantSession tenant, String customerId, String dueDate) {
    return (String)
        api.call(
                HttpMethod.POST,
                "/api/invoices",
                tenant.adminToken(),
                Map.of(
                    "customerId",
                    customerId,
                    "issueDate",
                    "2001-05-01",
                    "dueDate",
                    dueDate,
                    "lines",
                    java.util.List.of(
                        Map.of("description", "Draft", "quantity", "1", "unitPrice", "1"))),
                Map.class)
            .getBody()
            .get("id");
  }

  private String status(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT status FROM invoices WHERE id = ?::uuid", String.class, invoiceId);
  }

  private String updatedAt(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT updated_at::text FROM invoices WHERE id = ?::uuid", String.class, invoiceId);
  }
}
