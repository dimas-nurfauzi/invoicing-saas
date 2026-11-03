package io.github.dimasnurfauzi.invoicing.tenant;

public class SlugAlreadyTakenException extends RuntimeException {

  public SlugAlreadyTakenException(String slug) {
    super("Tenant slug '%s' is already taken".formatted(slug));
  }
}
