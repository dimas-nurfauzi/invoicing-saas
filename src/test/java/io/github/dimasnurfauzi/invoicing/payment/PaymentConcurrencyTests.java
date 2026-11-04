package io.github.dimasnurfauzi.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Concurrent payments are serialized by the invoice row lock: the invoice is never overpaid. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentConcurrencyTests {

  private static final int THREADS = 10;

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  @Test
  void parallelPaymentsNeverExceedTheTotal() throws Exception {
    TestTenants api = new TestTenants(rest);
    TenantSession tenant = api.register("pay-race");
    String customerId = api.createCustomer(tenant.adminToken(), "Race Payer");
    String invoiceId = api.createIssuedInvoice(tenant.adminToken(), customerId);

    // 10 × 20000.00 = 200000.00 against a total of 111000.00: only 5 can fit.
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    List<ResponseEntity<Map>> responses = new ArrayList<>();
    try {
      List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(() -> {
              start.await();
              return api.pay(tenant.adminToken(), invoiceId, "20000");
            }));
      }
      start.countDown();
      for (Future<ResponseEntity<Map>> future : futures) {
        responses.add(future.get());
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(responses.stream().filter(r -> r.getStatusCode().equals(HttpStatus.CREATED)))
        .hasSize(5);
    assertThat(responses.stream().filter(r -> r.getStatusCode().equals(HttpStatus.BAD_REQUEST)))
        .hasSize(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT amount_paid::text FROM invoices WHERE id = ?::uuid",
                String.class,
                invoiceId))
        .isEqualTo("100000.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0)::text FROM payments WHERE invoice_id = ?::uuid",
                String.class,
                invoiceId))
        .isEqualTo("100000.00");
  }
}
