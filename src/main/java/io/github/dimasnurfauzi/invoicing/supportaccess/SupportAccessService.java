package io.github.dimasnurfauzi.invoicing.supportaccess;

import io.github.dimasnurfauzi.invoicing.common.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant side of support access: the caller's tenant (from the token) grants, lists and revokes
 * access, and reads the access log. Everything is scoped by the tenant filter.
 */
@Service
public class SupportAccessService {

  private final SupportAccessGrantRepository grants;
  private final SupportAccessLogRepository logs;
  private final Clock clock = Clock.systemUTC();

  public SupportAccessService(
      SupportAccessGrantRepository grants, SupportAccessLogRepository logs) {
    this.grants = grants;
    this.logs = logs;
  }

  @Transactional
  public GrantResponse grant(int durationHours, UUID grantedBy) {
    Instant now = clock.instant();
    SupportAccessGrant grant =
        grants.save(
            new SupportAccessGrant(grantedBy, now, now.plus(Duration.ofHours(durationHours))));
    return GrantResponse.from(grant, now);
  }

  @Transactional(readOnly = true)
  public List<GrantResponse> list() {
    Instant now = clock.instant();
    return grants.findAllByOrderByCreatedAtDesc().stream()
        .map(grant -> GrantResponse.from(grant, now))
        .toList();
  }

  @Transactional
  public void revoke(UUID id) {
    grants
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Support access grant"))
        .revoke(clock.instant());
  }

  @Transactional(readOnly = true)
  public List<AccessLogResponse> logs() {
    return logs.findAllByOrderByAccessedAtDesc().stream().map(AccessLogResponse::from).toList();
  }
}
