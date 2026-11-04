package io.github.dimasnurfauzi.invoicing.ai.exception;

/** 502: every attempt produced output that failed validation. */
public class AiInvalidOutputException extends RuntimeException {

  public AiInvalidOutputException(int attempts) {
    super(
        "The AI couldn't produce a valid draft after %d attempts; rephrase the request or enter the invoice manually"
            .formatted(attempts));
  }
}
