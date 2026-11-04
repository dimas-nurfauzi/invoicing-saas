package io.github.dimasnurfauzi.invoicing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.ai.client.InvoiceDraftModel.Reply;
import io.github.dimasnurfauzi.invoicing.support.ScriptedInvoiceDraftModel;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** AI drafts end to end with a scripted model: validation, retries, failures, matching, privacy. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AiDraftTests {

  private static final String REQUEST =
      "Tagih PT Sinar Terang: desain logo 1,5jt + revisi 2 jam @200rb, PPN 11%, jatuh tempo"
          + " 2026-12-04";

  /** A schema-valid reply for REQUEST (what the real model is expected to return). */
  private static final String VALID =
      """
      {"customerName": "PT Sinar Terang", "issueDate": "2026-11-04", "dueDate": "2026-12-04",
       "currency": "IDR", "notes": "",
       "lines": [
         {"description": "Desain logo", "quantity": "1", "unitPrice": "1500000",
          "discountPercent": "0", "taxRatePercent": "11"},
         {"description": "Revisi", "quantity": "2", "unitPrice": "200000",
          "discountPercent": "0", "taxRatePercent": "11"}]}
      """;

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Autowired ScriptedInvoiceDraftModel model;

  private TestTenants api;
  private TenantSession tenant;
  private String customerId;

  @BeforeEach
  void setUp() {
    model.reset();
    api = new TestTenants(rest);
    tenant = api.register("ai");
    customerId = api.createCustomer(tenant.adminToken(), "PT Sinar Terang");
  }

  @Test
  void validReplyBecomesAReviewableDraftThatIsNotSaved() {
    model.reply(Reply.ofText(VALID));
    int invoicesBefore = invoiceCount();

    ResponseEntity<Map> response = draft(REQUEST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> draft = response.getBody();
    assertThat(draft)
        .containsEntry("customerId", customerId)
        .containsEntry("customerName", "PT Sinar Terang")
        .containsEntry("currency", "IDR")
        .containsEntry("issueDate", "2026-11-04")
        .containsEntry("dueDate", "2026-12-04")
        // (1500000 + 2 × 200000) × 1.11
        .containsEntry("previewTotal", "2109000.00")
        .containsEntry("attempts", 1);
    assertThat((List<?>) draft.get("warnings")).isEmpty();
    assertThat(((List<Map<String, Object>>) draft.get("lines")).get(1))
        .containsEntry("unitPrice", "200000")
        .containsEntry("taxRatePercent", "11");
    // Nothing is persisted until the user saves it through the normal endpoint.
    assertThat(invoiceCount()).isEqualTo(invoicesBefore);
  }

  @Test
  void draftCanBeSavedThroughTheNormalInvoiceEndpoint() {
    model.reply(Reply.ofText(VALID));
    Map<String, Object> draft = draft(REQUEST).getBody();

    Map<String, Object> body = new HashMap<>();
    for (String key : List.of("customerId", "currency", "issueDate", "dueDate", "notes", "lines")) {
      body.put(key, draft.get(key));
    }
    ResponseEntity<Map> saved =
        api.call(HttpMethod.POST, "/api/invoices", tenant.adminToken(), body, Map.class);

    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(saved.getBody()).containsEntry("total", draft.get("previewTotal"));
  }

  @Test
  void missingValuesGetDefaultsWithWarnings() {
    model.reply(
        Reply.ofText(
            """
            {"customerName": "Toko Belum Ada", "issueDate": "", "dueDate": "", "currency": "",
             "notes": "PO-77",
             "lines": [{"description": "Jasa", "quantity": "1", "unitPrice": "100000",
                        "discountPercent": "", "taxRatePercent": ""}]}
            """));

    Map<String, Object> draft = draft("jasa 100rb buat Toko Belum Ada, PO-77").getBody();

    assertThat(draft)
        .containsEntry("customerId", null)
        .containsEntry("currency", "IDR")
        .containsEntry("notes", "PO-77")
        .containsEntry("previewTotal", "100000.00");
    assertThat((List<String>) draft.get("warnings"))
        .anyMatch(w -> w.contains("No customer named"))
        .anyMatch(w -> w.contains("No invoice date"))
        .anyMatch(w -> w.contains("No due date"))
        .anyMatch(w -> w.contains("assuming IDR"));
  }

  @Test
  void invalidOutputIsRetriedWithTheErrorsInThePrompt() {
    model.reply(
        Reply.ofText("not json at all"),
        Reply.ofText(VALID.replace("\"1500000\"", "\"1,5jt\"")),
        Reply.ofText(VALID));

    ResponseEntity<Map> response = draft(REQUEST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("attempts", 3);
    List<String> prompts = model.userPrompts();
    assertThat(prompts).hasSize(3);
    assertThat(prompts.get(0)).doesNotContain("previous attempt");
    assertThat(prompts.get(1)).contains("not valid JSON");
    assertThat(prompts.get(2)).contains("lines[0].unitPrice");
  }

  @Test
  void givesUpAfterThreeInvalidAttempts() {
    String schemaViolation = "{\"customerName\": \"X\"}";
    String negativeQuantity = VALID.replace("\"quantity\": \"2\"", "\"quantity\": \"-2\"");
    model.reply(
        Reply.ofText(schemaViolation),
        Reply.ofText(negativeQuantity),
        new Reply("", false, true, 0, 0));

    ResponseEntity<String> response = draftRaw(REQUEST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody()).contains("after 3 attempts");
    assertThat(model.userPrompts()).hasSize(3);
  }

  @Test
  void refusalIs422WithoutRetrying() {
    model.reply(Reply.refusal(), Reply.ofText(VALID));

    ResponseEntity<String> response = draftRaw(REQUEST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(model.userPrompts()).hasSize(1);
  }

  @Test
  void notConfiguredIs503AndNeverCallsTheModel() {
    model.setConfigured(false);

    ResponseEntity<String> response = draftRaw(REQUEST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).contains("not configured");
    assertThat(model.userPrompts()).isEmpty();
  }

  @Test
  void blankOrOversizedTextIsBadRequest() {
    assertThat(draftRaw(" ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(draftRaw("x".repeat(5001)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(model.userPrompts()).isEmpty();
  }

  @Test
  void customerMatchingStaysInTheCallersTenant() {
    TenantSession other = api.register("ai-other");
    api.createCustomer(other.adminToken(), "Beta Only Customer");
    model.reply(Reply.ofText(VALID.replace("PT Sinar Terang", "Beta Only Customer")));

    Map<String, Object> draft = draft("tagih Beta Only Customer").getBody();

    // Tenant B has a customer with that name; tenant A's draft must not pick it up.
    assertThat(draft).containsEntry("customerId", null);
    assertThat((List<String>) draft.get("warnings")).anyMatch(w -> w.contains("No customer named"));
  }

  @Test
  void ambiguousCustomerNameIsNotGuessed() {
    api.createCustomer(tenant.adminToken(), "pt sinar terang");
    model.reply(Reply.ofText(VALID));

    Map<String, Object> draft = draft(REQUEST).getBody();

    assertThat(draft).containsEntry("customerId", null);
    assertThat((List<String>) draft.get("warnings")).anyMatch(w -> w.contains("Several customers"));
  }

  @Test
  void promptCarriesOnlyTheRequestTextAndToday() {
    api.createCustomer(tenant.adminToken(), "Secret Customer List Entry");
    model.reply(Reply.ofText(VALID));

    draft(REQUEST);

    String prompt = model.userPrompts().get(0);
    assertThat(prompt)
        .contains("<billing_request>\n" + REQUEST + "\n</billing_request>")
        .contains("Today's date: ")
        .doesNotContain("Secret Customer List Entry")
        .doesNotContain(tenant.tenantId());
  }

  @Test
  void staffCanDraft() {
    String staffEmail = "staff@" + tenant.slug() + ".test";
    api.createUser(tenant.adminToken(), staffEmail, "STAFF");
    model.reply(Reply.ofText(VALID));

    ResponseEntity<String> response =
        api.call(
            HttpMethod.POST,
            "/api/invoices/ai-draft",
            api.login(tenant.slug(), staffEmail),
            Map.of("text", REQUEST),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private ResponseEntity<Map> draft(String text) {
    return api.call(
        HttpMethod.POST,
        "/api/invoices/ai-draft",
        tenant.adminToken(),
        Map.of("text", text),
        Map.class);
  }

  private ResponseEntity<String> draftRaw(String text) {
    return api.call(
        HttpMethod.POST,
        "/api/invoices/ai-draft",
        tenant.adminToken(),
        Map.of("text", text),
        String.class);
  }

  private int invoiceCount() {
    return jdbc.queryForObject("SELECT count(*) FROM invoices", Integer.class);
  }
}
