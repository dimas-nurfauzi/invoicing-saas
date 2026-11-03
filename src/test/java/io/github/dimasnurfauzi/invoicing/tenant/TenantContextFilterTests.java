package io.github.dimasnurfauzi.invoicing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterTests {

  private final TenantContextFilter filter =
      new TenantContextFilter(id -> Optional.of(TenantStatus.ACTIVE));
  private final UUID tenantId = UUID.randomUUID();

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void setsTenantFromJwtDuringRequestAndClearsAfter() throws Exception {
    authenticateWithTenant(tenantId);
    AtomicReference<UUID> seen = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (req, res) -> seen.set(TenantContext.current().orElse(null)));

    assertThat(seen.get()).isEqualTo(tenantId);
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void clearsTenantEvenWhenRequestFails() {
    authenticateWithTenant(tenantId);

    assertThatThrownBy(() ->
            filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, res) -> {
                  throw new ServletException("boom");
                }))
        .isInstanceOf(ServletException.class);

    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void leftoverTenantFromAPreviousRequestIsNotReused() throws Exception {
    // Simulates a pooled thread that somehow kept a value: an anonymous request must not see it.
    TenantContext.set(UUID.randomUUID());
    AtomicReference<UUID> seen = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (req, res) -> seen.set(TenantContext.current().orElse(null)));

    assertThat(seen.get()).isNull();
  }

  @Test
  void suspendedTenantIsRejectedWith403AndChainNotCalled() throws Exception {
    TenantContextFilter suspendedFilter =
        new TenantContextFilter(id -> Optional.of(TenantStatus.SUSPENDED));
    authenticateWithTenant(tenantId);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainCalled = new AtomicBoolean();

    suspendedFilter.doFilter(
        new MockHttpServletRequest(), response, (req, res) -> chainCalled.set(true));

    assertThat(chainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("Tenant is suspended");
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void unknownTenantIsRejected() throws Exception {
    TenantContextFilter unknownFilter = new TenantContextFilter(id -> Optional.empty());
    authenticateWithTenant(tenantId);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainCalled = new AtomicBoolean();

    unknownFilter.doFilter(
        new MockHttpServletRequest(), response, (req, res) -> chainCalled.set(true));

    assertThat(chainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
  }

  private static void authenticateWithTenant(UUID tenantId) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", "ADMIN")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
