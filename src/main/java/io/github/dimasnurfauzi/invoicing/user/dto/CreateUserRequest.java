package io.github.dimasnurfauzi.invoicing.user.dto;

import io.github.dimasnurfauzi.invoicing.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** No tenant id field: new users always join the caller's tenant. */
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotNull Role role) {

  @Override
  public String toString() {
    return "CreateUserRequest[email=%s, password=***, role=%s]".formatted(email, role);
  }
}
