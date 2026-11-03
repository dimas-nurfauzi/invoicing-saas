package io.github.dimasnurfauzi.invoicing.common;

/** Maps to 409 Conflict. The message is returned to the client, so keep it free of internals. */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
