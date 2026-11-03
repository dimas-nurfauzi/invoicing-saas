package io.github.dimasnurfauzi.invoicing.tenant;

import io.github.dimasnurfauzi.invoicing.auth.JwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates {@link TenantContext} from the {@code tenant_id} claim of the already-validated JWT,
 * and always clears it when the request ends (Tomcat reuses threads, so a leftover value would leak
 * into the next request). Runs after the bearer token filter; never reads headers, path or body.
 */
public class TenantContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    TenantContext.clear();
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth instanceof JwtAuthenticationToken jwt) {
        TenantContext.set(UUID.fromString(jwt.getToken().getClaimAsString(JwtClaims.TENANT_ID)));
      }
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
