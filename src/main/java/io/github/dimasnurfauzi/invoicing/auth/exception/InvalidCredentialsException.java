package io.github.dimasnurfauzi.invoicing.auth.exception;

/** One message for unknown account and wrong password: don't reveal which. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    this("Invalid tenant, email or password");
  }

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
