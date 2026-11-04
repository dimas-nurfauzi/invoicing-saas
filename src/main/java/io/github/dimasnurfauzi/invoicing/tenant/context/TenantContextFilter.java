package io.github.dimasnurfauzi.invoicing.tenant.context;

import io.github.dimasnurfauzi.invoicing.security.jwt.JwtClaims;
import io.github.dimasnurfauzi.invoicing.tenant.entity.TenantStatus;
import io.github.dimasnurfauzi.invoicing.tenant.exception.TenantSuspendedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates {@link TenantContext} from the {@code tenant_id} claim of the already-validated JWT,
 * and always clears it when the request ends (Tomcat reuses threads, so a leftover value would leak
 * into the next request). Runs after the bearer token filter; never reads headers, path or body.
 *
 * <p>Also rejects requests for a suspended (or no longer existing) tenant with 403, so a suspension
 * takes effect immediately instead of when the token expires, and puts tenant_id / user_id into the
 * logging MDC.
 */
public class TenantContextFilter extends OncePerRequestFilter {

  static final String MDC_TENANT_ID = "tenant_id";
  static final String MDC_USER_ID = "user_id";

  private static final String SUSPENDED_PROBLEM =
      "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\""
          + TenantSuspendedException.MESSAGE
          + "\"}";

  /** Looks up a tenant's status; {@code TenantRepository::findStatusById} in production. */
  @FunctionalInterface
  public interface TenantStatusLookup {
    Optional<TenantStatus> statusOf(UUID tenantId);
  }

  private final TenantStatusLookup statusLookup;

  public TenantContextFilter(TenantStatusLookup statusLookup) {
    this.statusLookup = statusLookup;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    TenantContext.clear();
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth instanceof JwtAuthenticationToken jwt) {
        UUID tenantId = UUID.fromString(jwt.getToken().getClaimAsString(JwtClaims.TENANT_ID));
        MDC.put(MDC_TENANT_ID, tenantId.toString());
        MDC.put(MDC_USER_ID, jwt.getToken().getSubject());
        if (statusLookup.statusOf(tenantId).filter(s -> s == TenantStatus.ACTIVE).isEmpty()) {
          writeSuspended(response);
          return;
        }
        TenantContext.set(tenantId);
      }
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      MDC.remove(MDC_TENANT_ID);
      MDC.remove(MDC_USER_ID);
    }
  }

  private static void writeSuspended(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(SUSPENDED_PROBLEM);
  }
}
