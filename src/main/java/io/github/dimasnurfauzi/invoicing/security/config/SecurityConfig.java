package io.github.dimasnurfauzi.invoicing.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.dimasnurfauzi.invoicing.security.jwt.JwtClaims;
import io.github.dimasnurfauzi.invoicing.security.jwt.JwtService;
import io.github.dimasnurfauzi.invoicing.tenant.context.TenantContextFilter;
import io.github.dimasnurfauzi.invoicing.tenant.repository.TenantRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security with two separate filter chains:
 *
 * <ul>
 *   <li>{@code /api/platform/**}: platform admin API. Accepts only platform tokens (scope=platform,
 *       role PLATFORM_ADMIN, no tenant_id). No tenant context.
 *   <li>everything else: tenant API. Accepts only tenant tokens (tenant_id required, no scope).
 *       After authentication, {@link TenantContextFilter} sets the tenant from the token.
 * </ul>
 *
 * A token of one kind is rejected (401) by the other chain's decoder, so the separation does not
 * depend on controller code. Tokens are HS256-signed by this app ({@link JwtService}).
 *
 * <p>CORS ({@link CorsProperties}) applies to the tenant chain only, for the browser frontend. The
 * platform API has no browser client and stays same-origin only.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain platformFilterChain(HttpSecurity http, JwtProperties props) throws Exception {
    stateless(http)
        .securityMatcher("/api/platform/**")
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, "/api/platform/auth/login")
                .permitAll()
                .anyRequest()
                .hasRole(JwtClaims.PLATFORM_ADMIN_ROLE))
        .oauth2ResourceServer(rs ->
            rs.jwt(jwt ->
                jwt.decoder(platformJwtDecoder(props))
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain tenantFilterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, TenantRepository tenants, CorsProperties cors)
      throws Exception {
    if (cors.enabled()) {
      http.cors(c -> c.configurationSource(corsSource(cors)));
    } else {
      http.cors(AbstractHttpConfigurer::disable);
    }
    stateless(http)
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, "/api/tenants", "/api/auth/login")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                .permitAll()
                // API docs; switched off entirely with SPRINGDOC_ENABLED=false (then 404).
                .requestMatchers(
                    HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                // Error dispatches for permitted requests (e.g. 400s rendered by Boot).
                .requestMatchers("/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(rs ->
            rs.jwt(jwt ->
                jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterAfter(
            new TenantContextFilter(tenants::findStatusById),
            BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Exact origins only, no credentials (the token travels in the Authorization header, not a
   * cookie). Not a bean: a {@code corsConfigurationSource} bean would also be picked up by the
   * platform chain.
   */
  private static CorsConfigurationSource corsSource(CorsProperties cors) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(cors.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
    config.setExposedHeaders(List.of("X-Request-Id", "Location"));
    config.setAllowCredentials(false);
    config.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /** Decoder for tenant tokens (the only {@link JwtDecoder} bean). */
  @Bean
  JwtDecoder jwtDecoder(JwtProperties props) {
    return decoder(
        props,
        uuid(JwtClaimNames.SUB),
        uuid(JwtClaims.TENANT_ID),
        required(JwtClaims.ROLE),
        // A platform token must never pass as a tenant token.
        absent(JwtClaims.SCOPE));
  }

  /** Decoder for platform admin tokens. Not a bean, so it can't be picked up by accident. */
  private static JwtDecoder platformJwtDecoder(JwtProperties props) {
    return decoder(
        props,
        uuid(JwtClaimNames.SUB),
        equalTo(JwtClaims.SCOPE, JwtClaims.PLATFORM_SCOPE),
        equalTo(JwtClaims.ROLE, JwtClaims.PLATFORM_ADMIN_ROLE),
        absent(JwtClaims.TENANT_ID));
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties props) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static HttpSecurity stateless(HttpSecurity http) throws Exception {
    // No cookies or sessions: the bearer token is the only credential, so CSRF does not apply.
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }

  @SafeVarargs
  private static JwtDecoder decoder(JwtProperties props, OAuth2TokenValidator<Jwt>... claimChecks) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey(props))
            // Pin the algorithm: rejects "none" and any other alg in the header.
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(props.issuer());
    OAuth2TokenValidator<Jwt> all =
        new DelegatingOAuth2TokenValidator<>(
            defaults, new DelegatingOAuth2TokenValidator<>(claimChecks));
    decoder.setJwtValidator(all);
    return decoder;
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  private static SecretKey secretKey(JwtProperties props) {
    return new SecretKeySpec(props.secretBytes(), "HmacSHA256");
  }

  private static JwtClaimValidator<Object> required(String claim) {
    return new JwtClaimValidator<>(claim, value -> value != null && !value.toString().isBlank());
  }

  private static JwtClaimValidator<Object> absent(String claim) {
    return new JwtClaimValidator<>(claim, value -> value == null);
  }

  private static JwtClaimValidator<Object> equalTo(String claim, String expected) {
    return new JwtClaimValidator<>(
        claim, value -> value != null && expected.equals(value.toString()));
  }

  private static JwtClaimValidator<Object> uuid(String claim) {
    return new JwtClaimValidator<>(claim, value -> value != null && isUuid(value.toString()));
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
