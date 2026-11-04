package io.github.dimasnurfauzi.invoicing.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.ai.*}. The API key comes from {@code ANTHROPIC_API_KEY}; when it's empty the app still
 * starts and the draft endpoint answers 503. {@code toString()} never prints the key.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(
    String apiKey, String model, String effort, long maxTokens, Duration timeout, int maxAttempts) {

  public AiProperties {
    apiKey = apiKey == null ? "" : apiKey.trim();
    if (model == null || model.isBlank()) {
      model = "claude-opus-5-5";
    }
    if (effort == null || effort.isBlank()) {
      effort = "low";
    }
    if (maxTokens <= 0) {
      maxTokens = 16000;
    }
    if (timeout == null) {
      timeout = Duration.ofSeconds(60);
    }
    if (maxAttempts <= 0) {
      maxAttempts = 3;
    }
  }

  @Override
  public String toString() {
    return "AiProperties[apiKey=%s, model=%s, effort=%s, maxTokens=%d, timeout=%s, maxAttempts=%d]"
        .formatted(
            apiKey.isEmpty() ? "<unset>" : "***", model, effort, maxTokens, timeout, maxAttempts);
  }
}
