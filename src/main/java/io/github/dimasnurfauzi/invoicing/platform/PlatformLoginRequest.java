package io.github.dimasnurfauzi.invoicing.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformLoginRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {

  @Override
  public String toString() {
    return "PlatformLoginRequest[email=%s, password=***]".formatted(email);
  }
}
