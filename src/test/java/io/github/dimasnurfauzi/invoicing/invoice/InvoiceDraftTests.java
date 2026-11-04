package io.github.dimasnurfauzi.invoicing.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.ArrayList;
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

/** Draft invoices within one tenant: computed totals, editing, deleting, filters, validation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceDraftTests {

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenant;
  private String token;
  private String customerId;

  @BeforeEach
  void tenantWithCustomer() {
    api = new TestTenants(rest);
    tenant = api.register("inv");
    token = tenant.adminToken();
    customerId = api.createCustomer(token, "PT Sinar Terang");
  }

  @Test
  void createComputesAndStoresTotals() {
    Map<String, Object> body = body(customerId);
    body.put(
        "lines",
        List.of(
            line("Desain logo", "1", "1500000", "0", "11"),
            line("Revisi", "2.5", "200000", "10", "11")));
    // Amounts in the request are ignored; they are always computed.
    body.put("total", "1");

    ResponseEntity<Map> created =
        api.call(HttpMethod.POST, "/api/invoices", token, body, Map.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Map<String, Object> invoice = created.getBody();
    assertThat(invoice)
        .containsEntry("status", "DRAFT")
        .containsEntry("invoiceNumber", null)
        .containsEntry("currency", "IDR")
        .containsEntry("subtotal", "2000000.00")
        .containsEntry("discountTotal", "50000.00")
        .containsEntry("taxTotal", "214500.00")
        .containsEntry("total", "2164500.00")
        .doesNotContainKey("tenantId");
    assertThat((Map<String, Object>) invoice.get("customer"))
        .containsEntry("id", customerId)
        .containsEntry("name", "PT Sinar Terang");
    List<Map<String, Object>> lines = (List<Map<String, Object>>) invoice.get("lines");
    assertThat(lines).extracting(l -> l.get("lineNo")).containsExactly(1, 2);
    assertThat(lines.get(1))
        .containsEntry("quantity", "2.500")
        .containsEntry("subtotal", "500000.00")
        .containsEntry("discount", "50000.00")
        .containsEntry("tax", "49500.00")
        .containsEntry("total", "499500.00");

    // Same values after a reload from the database.
    Map<String, Object> reloaded = get((String) invoice.get("id")).getBody();
    assertThat(reloaded).containsEntry("total", "2164500.00");
    assertThat(((List<Map<String, Object>>) reloaded.get("lines")).get(1))
        .containsEntry("quantity", "2.500");
    assertThat(
            jdbc.queryForObject(
                "SELECT total::text FROM invoices WHERE id = ?::uuid",
                String.class,
                invoice.get("id")))
        .isEqualTo("2164500.00");
  }

  @Test
  void updateReplacesLinesAndRecomputes() {
    String id = api.createInvoice(token, customerId);
    Map<String, Object> body = body(customerId);
    body.put("lines", List.of(line("Konsultasi", "3", "50000", "0", "0")));
    body.put("notes", "Termin 2");

    ResponseEntity<Map> updated =
        api.call(HttpMethod.PUT, "/api/invoices/" + id, token, body, Map.class);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody())
        .containsEntry("total", "150000.00")
        .containsEntry("notes", "Termin 2");
    assertThat((List<?>) updated.getBody().get("lines")).hasSize(1);
    assertThat(lineCount(id)).isOne();
  }

  @Test
  void deleteRemovesDraftAndItsLines() {
    String id = api.createInvoice(token, customerId);

    assertThat(
            api.call(HttpMethod.DELETE, "/api/invoices/" + id, token, null, Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(lineCount(id)).isZero();
  }

  @Test
  void onlyDraftsCanBeEditedOrDeleted() {
    String id = api.createInvoice(token, customerId);
    jdbc.update("UPDATE invoices SET status = 'SENT' WHERE id = ?::uuid", id);

    assertThat(
            api.call(HttpMethod.PUT, "/api/invoices/" + id, token, body(customerId), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            api.call(HttpMethod.DELETE, "/api/invoices/" + id, token, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void customerMustExistAndNotBeArchived() {
    assertThat(
            api.call(
                    HttpMethod.POST,
                    "/api/invoices",
                    token,
                    body(UUID.randomUUID().toString()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    api.call(HttpMethod.DELETE, "/api/customers/" + customerId, token, null, Void.class);
    assertThat(
            api.call(HttpMethod.POST, "/api/invoices", token, body(customerId), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void listFiltersByStatusAndCustomer() {
    String otherCustomer = api.createCustomer(token, "Kopi Senja");
    String first = api.createInvoice(token, customerId);
    String second = api.createInvoice(token, otherCustomer);
    jdbc.update("UPDATE invoices SET status = 'SENT' WHERE id = ?::uuid", second);

    assertThat(ids("/api/invoices")).containsExactlyInAnyOrder(first, second);
    assertThat(ids("/api/invoices?customerId=" + customerId)).containsExactly(first);
    assertThat(ids("/api/invoices?status=DRAFT")).containsExactly(first);
    assertThat(ids("/api/invoices?status=SENT")).containsExactly(second);
    assertThat(
            api.call(HttpMethod.GET, "/api/invoices?status=NOPE", token, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void staffCanManageDrafts() {
    String staffEmail = "staff@" + tenant.slug() + ".test";
    api.createUser(token, staffEmail, "STAFF");
    String staffToken = api.login(tenant.slug(), staffEmail);

    String id = api.createInvoice(staffToken, customerId);

    assertThat(
            api.call(HttpMethod.DELETE, "/api/invoices/" + id, staffToken, null, Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void invalidInputIsBadRequest() {
    List<Map<String, Object>> invalid = new ArrayList<>();
    invalid.add(withLines(List.of()));
    invalid.add(withLines(List.of(line("Zero qty", "0", "1000", "0", "0"))));
    invalid.add(withLines(List.of(line("Four decimals", "1.0001", "1000", "0", "0"))));
    invalid.add(withLines(List.of(line("Three-decimal price", "1", "1.234", "0", "0"))));
    invalid.add(withLines(List.of(line("Negative price", "1", "-1", "0", "0"))));
    invalid.add(withLines(List.of(line("Tax over 100", "1", "1000", "0", "101"))));
    invalid.add(withLines(List.of(line(" ", "1", "1000", "0", "0"))));
    List<Map<String, Object>> tooMany = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      tooMany.add(line("Line " + i, "1", "1", "0", "0"));
    }
    invalid.add(withLines(tooMany));
    Map<String, Object> dueBeforeIssue = body(customerId);
    dueBeforeIssue.put("dueDate", "2026-10-01");
    invalid.add(dueBeforeIssue);
    Map<String, Object> lowercaseCurrency = body(customerId);
    lowercaseCurrency.put("currency", "idr");
    invalid.add(lowercaseCurrency);

    for (Map<String, Object> body : invalid) {
      assertThat(
              api.call(HttpMethod.POST, "/api/invoices", token, body, String.class).getStatusCode())
          .as(String.valueOf(body.get("lines")) + " / " + body.get("dueDate"))
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  private Map<String, Object> body(String customer) {
    return new HashMap<>(TestTenants.invoiceBody(customer));
  }

  private Map<String, Object> withLines(List<Map<String, Object>> lines) {
    Map<String, Object> body = body(customerId);
    body.put("lines", lines);
    return body;
  }

  private static Map<String, Object> line(
      String description, String quantity, String unitPrice, String discount, String tax) {
    return Map.of(
        "description", description,
        "quantity", quantity,
        "unitPrice", unitPrice,
        "discountPercent", discount,
        "taxRatePercent", tax);
  }

  private ResponseEntity<Map> get(String id) {
    return api.call(HttpMethod.GET, "/api/invoices/" + id, token, null, Map.class);
  }

  private List<String> ids(String path) {
    ResponseEntity<Map> response = api.call(HttpMethod.GET, path, token, null, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return ((List<Map<String, Object>>) response.getBody().get("content"))
        .stream().map(i -> (String) i.get("id")).toList();
  }

  private int lineCount(String invoiceId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM invoice_lines WHERE invoice_id = ?::uuid", Integer.class, invoiceId);
  }
}
