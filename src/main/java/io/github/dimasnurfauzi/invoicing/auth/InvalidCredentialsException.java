package io.github.dimasnurfauzi.invoicing.auth;

/** One message for unknown tenant, unknown email and wrong password: don't reveal which. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid tenant, email or password");
  }
}
