package io.github.dimasnurfauzi.invoicing.tenant;

import io.github.dimasnurfauzi.invoicing.common.ForbiddenException;

public class TenantSuspendedException extends ForbiddenException {

  public static final String MESSAGE = "Tenant is suspended";

  public TenantSuspendedException() {
    super(MESSAGE);
  }
}
