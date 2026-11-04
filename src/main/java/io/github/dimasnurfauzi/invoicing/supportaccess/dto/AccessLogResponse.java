package io.github.dimasnurfauzi.invoicing.supportaccess.dto;

import io.github.dimasnurfauzi.invoicing.supportaccess.entity.SupportAccessLog;
import java.time.Instant;
import java.util.UUID;

public record AccessLogResponse(
    UUID id,
    UUID grantId,
    UUID platformAdminId,
    String reason,
    String resource,
    Instant accessedAt) {

  public static AccessLogResponse from(SupportAccessLog log) {
    return new AccessLogResponse(
        log.getId(),
        log.getGrantId(),
        log.getPlatformAdminId(),
        log.getReason(),
        log.getResource(),
        log.getAccessedAt());
  }
}
