package io.github.dimasnurfauzi.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/** Partial payments, becoming PAID, and what can't be paid. Invoice total is 111000.00. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentTests {

  @Autowired TestRestTemplate rest;

  private TestTenants api;
  private TenantSession tenant;
  private String token;
  private String customerId;
  private String invoiceId;

  @BeforeEach
  void issuedInvoice() {
    api = new TestTenants(rest);
    tenant = api.register("pay");
    token = tenant.adminToken();
    customerId = api.createCustomer(token, "PT Bayar");
    invoiceId = api.createIssuedInvoice(token, customerId);
  }

  @Test
  void partialPaymentsAddUpUntilPaid() {
    ResponseEntity<Map> first = api.pay(token, invoiceId, "50000");

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat((Map<String, Object>) first.getBody().get("payment"))
        .containsEntry("amount", "50000.00")
        .containsEntry("method", "BANK_TRANSFER")
        .containsEntry("recordedBy", tenant.adminId());
    assertThat((Map<String, Object>) first.getBody().get("invoice"))
        .containsEntry("status", "SENT")
        .containsEntry("amountPaid", "50000.00")
        .containsEntry("balanceDue", "61000.00")
        .containsEntry("paidAt", null);

    Map<String, Object> afterSecond =
        (Map<String, Object>) api.pay(token, invoiceId, "61000.00").getBody().get("invoice");

    assertThat(afterSecond)
        .containsEntry("status", "PAID")
        .containsEntry("amountPaid", "111000.00")
        .containsEntry("balanceDue", "0.00");
    assertThat(afterSecond.get("paidAt")).isNotNull();

    ResponseEntity<List> history =
        api.call(
            HttpMethod.GET, "/api/invoices/" + invoiceId + "/payments", token, null, List.class);
    assertThat((List<Map<String, Object>>) history.getBody())
        .extracting(p -> p.get("amount"))
        .containsExactly("50000.00", "61000.00");
  }

  @Test
  void overpaymentIsRejected() {
    api.pay(token, invoiceId, "100000");

    ResponseEntity<Map> tooMuch = api.pay(token, invoiceId, "11000.01");

    assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invoice().get("amountPaid")).isEqualTo("100000.00");
  }

  @Test
  void onlySentOrOverdueInvoicesCanBePaid() {
    String draft = api.createInvoice(token, customerId);
    assertThat(api.pay(token, draft, "1").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    api.pay(token, invoiceId, "111000");
    assertThat(api.pay(token, invoiceId, "1").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    String toVoid = api.createIssuedInvoice(token, customerId);
    api.call(
        HttpMethod.POST,
        "/api/invoices/" + toVoid + "/void",
        token,
        Map.of("reason", "cancelled"),
        Map.class);
    assertThat(api.pay(token, toVoid, "1").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void invoiceWithPaymentsCannotBeVoided() {
    api.pay(token, invoiceId, "1000");

    ResponseEntity<String> voidIt =
        api.call(
            HttpMethod.POST,
            "/api/invoices/" + invoiceId + "/void",
            token,
            Map.of("reason", "too late"),
            String.class);

    assertThat(voidIt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(invoice().get("status")).isEqualTo("SENT");
  }

  @Test
  void invalidPaymentsAreBadRequest() {
    String future = LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();
    for (Map<String, Object> body :
        List.<Map<String, Object>>of(
            payment("0"),
            payment("-5"),
            payment("1.001"),
            withField(payment("10"), "paidOn", future),
            withField(payment("10"), "method", "CHEQUE"))) {
      assertThat(
              api.call(
                      HttpMethod.POST,
                      "/api/invoices/" + invoiceId + "/payments",
                      token,
                      body,
                      String.class)
                  .getStatusCode())
          .as(body.toString())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(invoice().get("amountPaid")).isEqualTo("0.00");
  }

  @Test
  void staffCanRecordPayments() {
    String staffEmail = "staff@" + tenant.slug() + ".test";
    api.createUser(token, staffEmail, "STAFF");
    String staffToken = api.login(tenant.slug(), staffEmail);

    assertThat(api.pay(staffToken, invoiceId, "1000").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  private Map<String, Object> invoice() {
    return api.call(HttpMethod.GET, "/api/invoices/" + invoiceId, token, null, Map.class).getBody();
  }

  private static Map<String, Object> payment(String amount) {
    Map<String, Object> body = new HashMap<>();
    body.put("amount", amount);
    body.put("paidOn", LocalDate.now(ZoneOffset.UTC).toString());
    body.put("method", "CASH");
    return body;
  }

  private static Map<String, Object> withField(Map<String, Object> body, String key, Object value) {
    body.put(key, value);
    return body;
  }
}
