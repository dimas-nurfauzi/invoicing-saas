package io.github.dimasnurfauzi.invoicing.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public sign-up payload. Deliberately has no tenant id: the tenant is created here, and every
 * later request takes its tenant from the authenticated principal.
 */
public record TenantRegistrationRequest(
    @NotBlank @Size(max = 200) String tenantName,
    @NotBlank
        @Size(min = 3, max = 63)
        @Pattern(
            regexp = "^[a-z0-9](-?[a-z0-9])*$",
            message = "must be lowercase letters, digits and single hyphens")
        String tenantSlug,
    @NotBlank @Email @Size(max = 254) String adminEmail,
    // BCrypt only uses the first 72 bytes; reject longer input instead of silently truncating.
    @NotBlank @Size(min = 8, max = 72) String adminPassword) {

  @Override
  public String toString() {
    return "TenantRegistrationRequest[tenantName=%s, tenantSlug=%s, adminEmail=%s, adminPassword=***]"
        .formatted(tenantName, tenantSlug, adminEmail);
  }
}
