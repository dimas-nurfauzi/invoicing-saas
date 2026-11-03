package io.github.dimasnurfauzi.invoicing.auth;

/** Custom claim names. {@code sub} holds the user id. */
public final class JwtClaims {

  public static final String TENANT_ID = "tenant_id";
  public static final String ROLE = "role";

  private JwtClaims() {}
}
