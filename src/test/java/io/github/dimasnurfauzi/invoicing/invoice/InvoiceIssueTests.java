package io.github.dimasnurfauzi.invoicing.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.HashMap;
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

/** Issuing and voiding within one tenant: numbers, bill-to snapshot, allowed transitions, roles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceIssueTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenant;
  private String token;
  private String customerId;

  @BeforeEach
  void tenantWithCustomer() {
    api = new TestTenants(rest);
    tenant = api.register("issue");
    token = tenant.adminToken();
    customerId = api.createCustomer(token, "PT Sinar Terang");
  }

  @Test
  void issueAssignsSequentialNumbersAndFreezesBillTo() {
    String first = api.createInvoice(token, customerId);
    String second = api.createInvoice(token, customerId);

    ResponseEntity<Map> issued = issue(first);

    assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(issued.getBody())
        .containsEntry("status", "SENT")
        .containsEntry("invoiceNumber", "INV-2026-0001");
    assertThat(issued.getBody().get("issuedAt")).isNotNull();
    assertThat((Map<String, Object>) issued.getBody().get("billTo"))
        .containsEntry("name", "PT Sinar Terang");
    assertThat(issue(second).getBody()).containsEntry("invoiceNumber", "INV-2026-0002");

    // Renaming the customer later doesn't change the sent invoice.
    api.call(
        HttpMethod.PUT,
        "/api/customers/" + customerId,
        token,
        Map.of("name", "PT Sinar Terang Baru"),
        Map.class);
    Map<String, Object> reloaded =
        api.call(HttpMethod.GET, "/api/invoices/" + first, token, null, Map.class).getBody();
    assertThat((Map<String, Object>) reloaded.get("billTo"))
        .containsEntry("name", "PT Sinar Terang");
  }

  @Test
  void numbersFollowTheIssueDateYear() {
    Map<String, Object> nextYear = new HashMap<>(TestTenants.invoiceBody(customerId));
    nextYear.put("issueDate", "2027-01-05");
    nextYear.put("dueDate", "2027-02-05");
    String id =
        (String)
            api.call(HttpMethod.POST, "/api/invoices", token, nextYear, Map.class)
                .getBody()
                .get("id");

    assertThat(issue(api.createInvoice(token, customerId)).getBody())
        .containsEntry("invoiceNumber", "INV-2026-0001");
    assertThat(issue(id).getBody()).containsEntry("invoiceNumber", "INV-2027-0001");
  }

  @Test
  void issuedInvoiceCannotBeIssuedEditedOrDeletedAgain() {
    String id = api.createInvoice(token, customerId);
    issue(id);

    assertThat(issue(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            api.call(
                    HttpMethod.PUT,
                    "/api/invoices/" + id,
                    token,
                    TestTenants.invoiceBody(customerId),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            api.call(HttpMethod.DELETE, "/api/invoices/" + id, token, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void failedIssueDoesNotConsumeANumber() {
    String id = api.createInvoice(token, customerId);
    api.call(HttpMethod.DELETE, "/api/customers/" + customerId, token, null, Void.class);

    assertThat(issue(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    api.call(HttpMethod.POST, "/api/customers/" + customerId + "/restore", token, null, Map.class);
    assertThat(issue(id).getBody()).containsEntry("invoiceNumber", "INV-2026-0001");
  }

  @Test
  void voidKeepsTheNumberAndIsFinal() {
    String id = api.createInvoice(token, customerId);
    issue(id);

    ResponseEntity<Map> voided = voidInvoice(id, token, "Salah input harga");

    assertThat(voided.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(voided.getBody())
        .containsEntry("status", "VOID")
        .containsEntry("invoiceNumber", "INV-2026-0001")
        .containsEntry("voidReason", "Salah input harga");
    assertThat(voidInvoice(id, token, "again").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    // The next invoice continues the sequence: voided numbers are not reused.
    assertThat(issue(api.createInvoice(token, customerId)).getBody())
        .containsEntry("invoiceNumber", "INV-2026-0002");
  }

  @Test
  void draftsAreDeletedNotVoided() {
    String id = api.createInvoice(token, customerId);

    assertThat(voidInvoice(id, token, "not issued").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void voidNeedsAdminAndAReason() {
    String staffEmail = "staff@" + tenant.slug() + ".test";
    api.createUser(token, staffEmail, "STAFF");
    String staffToken = api.login(tenant.slug(), staffEmail);
    String id = api.createInvoice(staffToken, customerId);

    // STAFF may issue...
    assertThat(
            api.call(HttpMethod.POST, "/api/invoices/" + id + "/issue", staffToken, null, Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    // ...but not void.
    assertThat(voidInvoice(id, staffToken, "staff try").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(voidInvoice(id, token, "").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?::uuid", String.class, id))
        .isEqualTo("SENT");
  }

  private ResponseEntity<Map> issue(String id) {
    return api.call(HttpMethod.POST, "/api/invoices/" + id + "/issue", token, null, Map.class);
  }

  private ResponseEntity<Map> voidInvoice(String id, String as, String reason) {
    return api.call(
        HttpMethod.POST, "/api/invoices/" + id + "/void", as, Map.of("reason", reason), Map.class);
  }
}
