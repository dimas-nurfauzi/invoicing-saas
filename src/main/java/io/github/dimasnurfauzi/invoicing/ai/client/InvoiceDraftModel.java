package io.github.dimasnurfauzi.invoicing.ai.client;

/**
 * The LLM behind draft extraction. One stateless request per call (retries send a fresh request
 * with the previous errors in the prompt, rather than replaying history). Implemented by {@link
 * ClaudeInvoiceDraftModel}; tests replace it with a scripted fake.
 */
public interface InvoiceDraftModel {

  /** False when no API key is configured; the endpoint then answers 503. */
  boolean isConfigured();

  Reply complete(String system, String user);

  /**
   * @param text the model's text output (expected to be JSON), possibly empty
   * @param refused the model declined (stop reason "refusal"), even after server-side fallback
   * @param truncated output was cut off at max_tokens
   */
  record Reply(
      String text, boolean refused, boolean truncated, long inputTokens, long outputTokens) {

    public static Reply ofText(String text) {
      return new Reply(text, false, false, 0, 0);
    }

    public static Reply refusal() {
      return new Reply("", true, false, 0, 0);
    }
  }
}
