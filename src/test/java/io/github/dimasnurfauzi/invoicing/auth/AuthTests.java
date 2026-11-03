package io.github.dimasnurfauzi.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.dimasnurfauzi.invoicing.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthTests {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired TestRestTemplate rest;
  @Autowired JwtEncoder jwtEncoder;
  @Autowired JwtDecoder jwtDecoder;
  @Autowired JwtProperties jwtProperties;

  private String slug;
  private String email;
  private String tenantId;
  private String userId;

  @BeforeEach
  void registerTenant() {
    slug = "t-" + UUID.randomUUID().toString().substring(0, 8);
    email = "owner-" + slug + "@example.com";
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        rest.postForObject(
            "/api/tenants",
            Map.of(
                "tenantName", "Auth Test",
                "tenantSlug", slug,
                "adminEmail", email,
                "adminPassword", PASSWORD),
            Map.class);
    tenantId = (String) body.get("tenantId");
    userId = (String) body.get("userId");
  }

  @Test
  void loginIssuesTokenWithSubTenantAndRole() {
    ResponseEntity<Map> response = login(slug, email.toUpperCase(), PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("tokenType", "Bearer");
    assertThat(((Number) response.getBody().get("expiresIn")).longValue())
        .isEqualTo(jwtProperties.ttl().toSeconds());

    Jwt jwt = jwtDecoder.decode((String) response.getBody().get("accessToken"));
    assertThat(jwt.getSubject()).isEqualTo(userId);
    assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(tenantId);
    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo(jwtProperties.issuer());
    assertThat(jwt.getClaims()).doesNotContainKey("email");
    assertThat(jwt.getExpiresAt())
        .isCloseTo(Instant.now().plus(jwtProperties.ttl()), within(30, ChronoUnit.SECONDS));
  }

  @Test
  void wrongTenantEmailOrPasswordAreIndistinguishable401s() {
    ResponseEntity<String> wrongPassword = loginRaw(slug, email, "wrong-password");
    ResponseEntity<String> unknownEmail = loginRaw(slug, "nobody@example.com", PASSWORD);
    ResponseEntity<String> unknownTenant = loginRaw("no-such-tenant", email, PASSWORD);

    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownTenant.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownEmail.getBody()).isEqualTo(wrongPassword.getBody());
    assertThat(unknownTenant.getBody()).isEqualTo(wrongPassword.getBody());
    assertThat(wrongPassword.getBody()).doesNotContain("accessToken");
  }

  @Test
  void blankLoginFieldsAreBadRequest() {
    assertThat(loginRaw("", email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void validTokenIsAccepted() {
    String token = (String) login(slug, email, PASSWORD).getBody().get("accessToken");

    ResponseEntity<Map> me = me(token);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody())
        .containsEntry("userId", userId)
        .containsEntry("tenantId", tenantId)
        .containsEntry("role", "ADMIN");
  }

  @Test
  void missingTokenIsUnauthorized() {
    assertThat(rest.getForEntity("/api/me", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void expiredTokenIsUnauthorized() {
    // Signed with the real key, but expired well beyond the default 60s clock skew.
    Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
    String token = sign(jwtEncoder, claims().issuedAt(past).expiresAt(past.plusSeconds(300)));

    ResponseEntity<Map> response = me(token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("expired");
  }

  @Test
  void tokenSignedWithAnotherKeyIsUnauthorized() {
    byte[] otherKey =
        "another-secret-that-is-long-enough-0123456789".getBytes(StandardCharsets.UTF_8);
    JwtEncoder attacker =
        new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
    Instant now = Instant.now();

    String token = sign(attacker, claims().issuedAt(now).expiresAt(now.plusSeconds(300)));

    assertThat(me(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tamperedPayloadIsUnauthorized() {
    String token = (String) login(slug, email, PASSWORD).getBody().get("accessToken");
    String[] parts = token.split("\\.");
    String forgedPayload =
        base64Url(
            "{\"sub\":\"%s\",\"tenant_id\":\"%s\",\"role\":\"ADMIN\",\"iss\":\"%s\",\"exp\":%d}"
                .formatted(
                    userId,
                    UUID.randomUUID(),
                    jwtProperties.issuer(),
                    Instant.now().plusSeconds(300).getEpochSecond()));

    String forged = parts[0] + "." + forgedPayload + "." + parts[2];

    assertThat(me(forged).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void unsignedAlgNoneTokenIsUnauthorized() {
    String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
    String payload =
        base64Url(
            "{\"sub\":\"%s\",\"tenant_id\":\"%s\",\"role\":\"ADMIN\",\"iss\":\"%s\",\"exp\":%d}"
                .formatted(
                    userId,
                    tenantId,
                    jwtProperties.issuer(),
                    Instant.now().plusSeconds(300).getEpochSecond()));

    assertThat(me(header + "." + payload + ".").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenMissingTenantClaimIsUnauthorized() {
    Instant now = Instant.now();
    JwtClaimsSet.Builder noTenant =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .subject(userId)
            .claim("role", "ADMIN")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300));

    ResponseEntity<Map> response = me(sign(jwtEncoder, noTenant));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("tenant_id");
  }

  @Test
  void garbageTokenIsUnauthorized() {
    assertThat(me("not-a-jwt").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private JwtClaimsSet.Builder claims() {
    return JwtClaimsSet.builder()
        .issuer(jwtProperties.issuer())
        .subject(userId)
        .claim("tenant_id", tenantId)
        .claim("role", "ADMIN");
  }

  private static String sign(JwtEncoder encoder, JwtClaimsSet.Builder claims) {
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  private static String base64Url(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("rawtypes")
  private ResponseEntity<Map> login(String tenantSlug, String email, String password) {
    return rest.postForEntity("/api/auth/login", loginBody(tenantSlug, email, password), Map.class);
  }

  private ResponseEntity<String> loginRaw(String tenantSlug, String email, String password) {
    return rest.postForEntity(
        "/api/auth/login", loginBody(tenantSlug, email, password), String.class);
  }

  private static Map<String, String> loginBody(String tenantSlug, String email, String password) {
    return Map.of("tenantSlug", tenantSlug, "email", email, "password", password);
  }

  @SuppressWarnings("rawtypes")
  private ResponseEntity<Map> me(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
  }
}
