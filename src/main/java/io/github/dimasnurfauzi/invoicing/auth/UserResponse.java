package io.github.dimasnurfauzi.invoicing.auth;

import java.time.Instant;
import java.util.UUID;

/** Public view of a user. No password hash, no tenant id. */
public record UserResponse(UUID id, String email, Role role, Instant createdAt) {

  static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
  }
}
