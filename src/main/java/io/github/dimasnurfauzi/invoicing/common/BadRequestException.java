package io.github.dimasnurfauzi.invoicing.common;

/** Maps to 400 for business-rule validation that Bean Validation can't express. */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
