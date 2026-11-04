package io.github.dimasnurfauzi.invoicing.common.exception;

/** Maps to 403. The message is returned to the client, so keep it free of internals. */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
