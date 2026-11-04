package io.github.dimasnurfauzi.invoicing.user.dto;

import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import io.github.dimasnurfauzi.invoicing.user.entity.User;
import java.time.Instant;
import java.util.UUID;

/** Public view of a user. No password hash, no tenant id. */
public record UserResponse(UUID id, String email, Role role, Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
  }
}
