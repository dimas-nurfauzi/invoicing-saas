package io.github.dimasnurfauzi.invoicing.security.jwt;

import io.github.dimasnurfauzi.invoicing.security.config.JwtProperties;
import io.github.dimasnurfauzi.invoicing.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens. Tenant tokens: sub (user id), tenant_id, role. Platform admin tokens: sub
 * (admin id), role PLATFORM_ADMIN, scope platform, and no tenant_id. Both carry iss, iat, exp and
 * no PII.
 */
@Service
public class JwtService {

  private final JwtEncoder encoder;
  private final JwtProperties props;
  private final Clock clock;

  public JwtService(JwtEncoder encoder, JwtProperties props) {
    this.encoder = encoder;
    this.props = props;
    this.clock = Clock.systemUTC();
  }

  public String issue(User user) {
    return encode(
        baseClaims(user.getId())
            .claim(JwtClaims.TENANT_ID, user.getTenantId().toString())
            .claim(JwtClaims.ROLE, user.getRole().name()));
  }

  public String issuePlatformAdmin(UUID adminId) {
    return encode(
        baseClaims(adminId)
            .claim(JwtClaims.ROLE, JwtClaims.PLATFORM_ADMIN_ROLE)
            .claim(JwtClaims.SCOPE, JwtClaims.PLATFORM_SCOPE));
  }

  private JwtClaimsSet.Builder baseClaims(UUID subject) {
    Instant now = clock.instant();
    return JwtClaimsSet.builder()
        .issuer(props.issuer())
        .subject(subject.toString())
        .issuedAt(now)
        .expiresAt(now.plus(props.ttl()));
  }

  private String encode(JwtClaimsSet.Builder claims) {
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  public long ttlSeconds() {
    return props.ttl().toSeconds();
  }
}
