package io.github.dimasnurfauzi.invoicing.ai.exception;

/** 503: the Claude API failed after the SDK's own retries (rate limit, outage, timeout). */
public class AiUnavailableException extends RuntimeException {

  public AiUnavailableException(Throwable cause) {
    super(
        "AI drafting is temporarily unavailable; try again later or enter the invoice manually",
        cause);
  }
}
