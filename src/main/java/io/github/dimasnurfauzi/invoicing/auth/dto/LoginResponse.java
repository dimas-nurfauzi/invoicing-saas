package io.github.dimasnurfauzi.invoicing.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

  public static LoginResponse bearer(String token, long expiresInSeconds) {
    return new LoginResponse(token, "Bearer", expiresInSeconds);
  }

  @Override
  public String toString() {
    return "LoginResponse[accessToken=***, tokenType=%s, expiresIn=%d]"
        .formatted(tokenType, expiresIn);
  }
}
