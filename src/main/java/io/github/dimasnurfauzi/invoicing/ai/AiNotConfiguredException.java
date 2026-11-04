package io.github.dimasnurfauzi.invoicing.ai;

/** 503: no ANTHROPIC_API_KEY configured. */
public class AiNotConfiguredException extends RuntimeException {

  public AiNotConfiguredException() {
    super("AI drafting is not configured");
  }
}
