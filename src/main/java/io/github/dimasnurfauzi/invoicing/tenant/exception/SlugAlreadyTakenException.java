package io.github.dimasnurfauzi.invoicing.tenant.exception;

import io.github.dimasnurfauzi.invoicing.common.exception.ConflictException;

public class SlugAlreadyTakenException extends ConflictException {

  public SlugAlreadyTakenException(String slug) {
    super("Tenant slug '%s' is already taken".formatted(slug));
  }
}
