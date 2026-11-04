package io.github.dimasnurfauzi.invoicing.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import io.github.dimasnurfauzi.invoicing.invoice.domain.InvoiceNumbers;
import io.github.dimasnurfauzi.invoicing.support.TestTenants;
import io.github.dimasnurfauzi.invoicing.support.TestTenants.TenantSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
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
 * CLAUDE.md: invoice numbers are unique per (tenant, year) and gapless under concurrency. Requests
 * are released together by a latch so they really race, through the real HTTP stack and PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceNumberingConcurrencyTests {

  private static final int INVOICES = 20;
  private static final int THREADS = 10;

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  private TestTenants api;
  private TenantSession tenant;
  private String customerId;

  @BeforeEach
  void freshTenant() {
    api = new TestTenants(rest);
    tenant = api.register("race");
    customerId = api.createCustomer(tenant.adminToken(), "Race Customer");
  }

  @Test
  void parallelIssuesGetUniqueGaplessNumbers() throws Exception {
    List<String> drafts =
        IntStream.range(0, INVOICES)
            .mapToObj(i -> api.createInvoice(tenant.adminToken(), customerId))
            .toList();

    List<ResponseEntity<Map>> responses = race(drafts);

    assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
    List<String> numbers =
        responses.stream().map(r -> (String) r.getBody().get("invoiceNumber")).toList();
    List<String> expected =
        IntStream.rangeClosed(1, INVOICES).mapToObj(n -> InvoiceNumbers.format(2026, n)).toList();
    // Unique and gapless: exactly 0001..0020, each once.
    assertThat(numbers).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    assertThat(counterValue()).isEqualTo(INVOICES);
  }

  @Test
  void sameDraftIssuedInParallelGetsExactlyOneNumber() throws Exception {
    String draft = api.createInvoice(tenant.adminToken(), customerId);
    List<String> sameDraftManyTimes = java.util.Collections.nCopies(THREADS, draft);

    List<ResponseEntity<Map>> responses = race(sameDraftManyTimes);

    assertThat(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK)).hasSize(1);
    assertThat(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT))
        .hasSize(THREADS - 1);
    // The losers never reached the counter: one number used, no gap.
    assertThat(counterValue()).isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT invoice_number FROM invoices WHERE id = ?::uuid", String.class, draft))
        .isEqualTo("INV-2026-0001");
  }

  /** Issues every id from a pool of {@value #THREADS} threads, all starting at the same moment. */
  private List<ResponseEntity<Map>> race(List<String> invoiceIds) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
      for (String id : invoiceIds) {
        Callable<ResponseEntity<Map>> call =
            () -> {
              start.await();
              return api.call(
                  HttpMethod.POST,
                  "/api/invoices/" + id + "/issue",
                  tenant.adminToken(),
                  null,
                  Map.class);
            };
        futures.add(pool.submit(call));
      }
      start.countDown();
      List<ResponseEntity<Map>> responses = new ArrayList<>();
      for (Future<ResponseEntity<Map>> future : futures) {
        responses.add(future.get());
      }
      return responses;
    } finally {
      pool.shutdownNow();
    }
  }

  private int counterValue() {
    return jdbc.queryForObject(
        "SELECT last_value FROM invoice_number_counters WHERE tenant_id = ?::uuid"
            + " AND number_year = 2026",
        Integer.class,
        tenant.tenantId());
  }
}
