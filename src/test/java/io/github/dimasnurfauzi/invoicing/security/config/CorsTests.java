package io.github.dimasnurfauzi.invoicing.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** CORS for the browser frontend; the test profile allows only {@code http://localhost:5173}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CorsTests {

  private static final String ALLOWED = "http://localhost:5173";

  @Autowired TestRestTemplate rest;

  @Test
  void preflightFromAllowedOriginIsAccepted() {
    ResponseEntity<String> response = preflight("/api/customers", ALLOWED, "POST");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    HttpHeaders headers = response.getHeaders();
    assertThat(headers.getAccessControlAllowOrigin()).isEqualTo(ALLOWED);
    assertThat(headers.getAccessControlAllowMethods()).contains(HttpMethod.POST);
    assertThat(headers.getAccessControlAllowHeaders())
        .map(String::toLowerCase)
        .contains("authorization", "content-type");
    assertThat(headers.getAccessControlAllowCredentials()).isFalse();
  }

  @Test
  void preflightFromOtherOriginIsRejected() {
    ResponseEntity<String> response = preflight("/api/customers", "https://evil.example", "GET");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  @Test
  void actualRequestFromAllowedOriginExposesRequestId() {
    HttpHeaders headers = new HttpHeaders();
    headers.setOrigin(ALLOWED);

    ResponseEntity<String> response =
        rest.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED);
    assertThat(response.getHeaders().getAccessControlExposeHeaders()).contains("X-Request-Id");
  }

  @Test
  void platformApiDoesNotAllowCors() {
    ResponseEntity<String> response = preflight("/api/platform/auth/login", ALLOWED, "POST");

    assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  private ResponseEntity<String> preflight(String path, String origin, String method) {
    HttpHeaders headers = new HttpHeaders();
    headers.setOrigin(origin);
    headers.setAccessControlRequestMethod(HttpMethod.valueOf(method));
    headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
    return rest.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
  }
}
