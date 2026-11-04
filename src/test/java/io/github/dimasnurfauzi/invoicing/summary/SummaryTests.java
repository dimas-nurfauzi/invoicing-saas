package io.github.dimasnurfauzi.invoicing.summary;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.invoice.job.OverdueInvoiceJob;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.time.LocalDate;
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

/**
 * Summary over a known set of invoices in one tenant, plus isolation: tenant B's invoices never
 * show up in tenant A's summary.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SummaryTests {

  @Autowired TestRestTemplate rest;
  @Autowired OverdueInvoiceJob job;

  private TestTenants api;
  private TenantSession tenant;
  private String token;

  /**
   * IDR (issued 2002): SENT 1000.00 with 400.00 paid; PAID 2000.00; OVERDUE 500.00 with 100.00
   * paid; VOID 9999.00; DRAFT 7777.00. USD (issued 2002): SENT 50.00. IDR issued 2003: SENT 300.00.
   */
  @BeforeEach
  void knownInvoices() {
    api = new TestTenants(rest);
    tenant = api.register("summary");
    token = tenant.adminToken();
    String customer = api.createCustomer(token, "Summary Customer");

    String sent = issued(customer, "2002-03-01", "2099-12-31", "IDR", "1000");
    api.pay(token, sent, "400");
    String paid = issued(customer, "2002-03-02", "2099-12-31", "IDR", "2000");
    api.pay(token, paid, "2000");
    String overdue = issued(customer, "2002-03-03", "2002-03-10", "IDR", "500");
    api.pay(token, overdue, "100");
    job.markOverdue(LocalDate.of(2002, 4, 1));
    String voided = issued(customer, "2002-03-04", "2099-12-31", "IDR", "9999");
    api.call(
        HttpMethod.POST,
        "/api/invoices/" + voided + "/void",
        token,
        Map.of("reason", "cancelled"),
        Map.class);
    api.call(
        HttpMethod.POST,
        "/api/invoices",
        token,
        Map.of(
            "customerId",
            customer,
            "issueDate",
            "2002-03-05",
            "dueDate",
            "2099-12-31",
            "lines",
            List.of(Map.of("description", "Draft", "quantity", "1", "unitPrice", "7777"))),
        Map.class);
    issued(customer, "2002-03-06", "2099-12-31", "USD", "50");
    issued(customer, "2003-01-15", "2099-12-31", "IDR", "300");
  }

  @Test
  void totalsPerCurrencyExcludeDraftsAndVoided() {
    Map<String, Map<String, Object>> byCurrency = summary("");

    assertThat(byCurrency.get("IDR"))
        .containsEntry("totalBilled", "3800.00") // 1000 + 2000 + 500 + 300
        .containsEntry("totalPaid", "2500.00") // 400 + 2000 + 100
        .containsEntry("totalOutstanding", "1300.00") // 600 + 400 + 300
        .containsEntry("totalOverdue", "400.00");
    assertThat((Map<String, Object>) byCurrency.get("IDR").get("invoiceCounts"))
        .containsEntry("DRAFT", 1)
        .containsEntry("SENT", 2)
        .containsEntry("PAID", 1)
        .containsEntry("OVERDUE", 1)
        .containsEntry("VOID", 1);
    // Never added to IDR.
    assertThat(byCurrency.get("USD"))
        .containsEntry("totalBilled", "50.00")
        .containsEntry("totalOutstanding", "50.00");
  }

  @Test
  void dateRangeFiltersOnIssueDate() {
    Map<String, Map<String, Object>> in2003 = summary("?from=2003-01-01&to=2003-12-31");

    assertThat(in2003).containsOnlyKeys("IDR");
    assertThat(in2003.get("IDR"))
        .containsEntry("totalBilled", "300.00")
        .containsEntry("totalOverdue", "0.00");
    assertThat(summary("?from=1990-01-01&to=1990-12-31")).isEmpty();
  }

  @Test
  void invalidRangeIsBadRequest() {
    assertThat(
            api.call(
                    HttpMethod.GET,
                    "/api/summary?from=2003-01-02&to=2003-01-01",
                    token,
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anotherTenantsInvoicesAreNotCounted() {
    TenantSession other = api.register("summary-other");
    String otherCustomer = api.createCustomer(other.adminToken(), "Other");
    api.createIssuedInvoice(
        other.adminToken(), otherCustomer, "2002-03-01", "2099-12-31", "IDR", "1000000");
    api.createIssuedInvoice(
        other.adminToken(), otherCustomer, "2002-03-01", "2099-12-31", "EUR", "5");

    Map<String, Map<String, Object>> mine = summary("");

    assertThat(mine).containsOnlyKeys("IDR", "USD");
    assertThat(mine.get("IDR")).containsEntry("totalBilled", "3800.00");
  }

  @Test
  void staffCanReadTheSummary() {
    String staffEmail = "staff@" + tenant.slug() + ".test";
    api.createUser(token, staffEmail, "STAFF");

    assertThat(
            api.call(
                    HttpMethod.GET,
                    "/api/summary",
                    api.login(tenant.slug(), staffEmail),
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private String issued(
      String customer, String issueDate, String dueDate, String currency, String amount) {
    return api.createIssuedInvoice(token, customer, issueDate, dueDate, currency, amount);
  }

  private Map<String, Map<String, Object>> summary(String query) {
    ResponseEntity<Map> response =
        api.call(HttpMethod.GET, "/api/summary" + query, token, null, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Map<String, Object>> byCurrency = new java.util.LinkedHashMap<>();
    for (Map<String, Object> entry :
        (List<Map<String, Object>>) response.getBody().get("currencies")) {
      byCurrency.put((String) entry.get("currency"), entry);
    }
    return byCurrency;
  }
}
