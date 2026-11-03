package io.github.dimasnurfauzi.invoicing.auth;

/** Custom claim names and values. {@code sub} holds the user (or platform admin) id. */
public final class JwtClaims {

  public static final String TENANT_ID = "tenant_id";
  public static final String ROLE = "role";

  /**
   * Present only on platform admin tokens ({@value #PLATFORM_SCOPE}). Tenant tokens never carry it,
   * and the tenant API rejects tokens that do.
   */
  public static final String SCOPE = "scope";

  public static final String PLATFORM_SCOPE = "platform";
  public static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

  private JwtClaims() {}
}
