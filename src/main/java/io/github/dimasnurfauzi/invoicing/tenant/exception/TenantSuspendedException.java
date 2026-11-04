package io.github.dimasnurfauzi.invoicing.tenant.exception;

import io.github.dimasnurfauzi.invoicing.common.exception.ForbiddenException;

public class TenantSuspendedException extends ForbiddenException {

  public static final String MESSAGE = "Tenant is suspended";

  public TenantSuspendedException() {
    super(MESSAGE);
  }
}
