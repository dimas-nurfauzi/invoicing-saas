package io.github.dimasnurfauzi.invoicing.tenant;

import io.github.dimasnurfauzi.invoicing.common.ConflictException;

public class SlugAlreadyTakenException extends ConflictException {

  public SlugAlreadyTakenException(String slug) {
    super("Tenant slug '%s' is already taken".formatted(slug));
  }
}
