package io.github.dimasnurfauzi.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoicingApplicationTests {

  @Autowired TestRestTemplate rest;

  @Autowired Flyway flyway;

  @Test
  void flywayAppliedAllMigrations() {
    assertThat(flyway.info().applied())
        .extracting(migration -> migration.getVersion().getVersion())
        .containsExactly("1", "2", "3", "4", "5");
  }

  @Test
  void healthEndpointIsUp() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void otherActuatorEndpointsRequireAuthentication() {
    assertThat(rest.getForEntity("/actuator/info", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void apiDocsArePublicAndDeclareBearerAuth() {
    ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);

    assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(docs.getBody())
        .contains("\"bearerAuth\"")
        .contains("/api/auth/login")
        .contains("/api/users/{id}");
    assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}
