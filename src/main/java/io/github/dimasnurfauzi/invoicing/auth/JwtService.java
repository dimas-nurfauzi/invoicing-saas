package io.github.dimasnurfauzi.invoicing.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues access tokens. Claims: sub (user id), tenant_id, role, iss, iat, exp. No PII. */
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
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .subject(user.getId().toString())
            .claim(JwtClaims.TENANT_ID, user.getTenantId().toString())
            .claim(JwtClaims.ROLE, user.getRole().name())
            .issuedAt(now)
            .expiresAt(now.plus(props.ttl()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long ttlSeconds() {
    return props.ttl().toSeconds();
  }
}
