package io.github.dimasnurfauzi.invoicing.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The real adapter, without network calls: configuration and request shape only. */
class ClaudeInvoiceDraftModelTests {

  private final InvoiceDraftSchema schema = new InvoiceDraftSchema(new ObjectMapper());

  private static AiProperties props(String apiKey) {
    return new AiProperties(apiKey, "claude-opus-5-5", "low", 16000, Duration.ofSeconds(60), 3);
  }

  @Test
  void withoutAnApiKeyItIsNotConfiguredAndNeverCalls() {
    ClaudeInvoiceDraftModel model = new ClaudeInvoiceDraftModel(props(""), schema);

    assertThat(model.isConfigured()).isFalse();
    assertThatThrownBy(() -> model.complete("s", "u")).isInstanceOf(AiNotConfiguredException.class);
  }

  @Test
  void requestUsesModelEffortSchemaAndRefusalFallback() {
    ClaudeInvoiceDraftModel model = new ClaudeInvoiceDraftModel(props("sk-test-not-real"), schema);

    MessageCreateParams params = model.params("system prompt", "user prompt");

    assertThat(model.isConfigured()).isTrue();
    assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
    assertThat(params.maxTokens()).isEqualTo(16000L);
    assertThat(params.outputConfig()).isPresent();
    assertThat(params.outputConfig().get().format()).isPresent();
    assertThat(params.fallbacks()).isPresent();
    assertThat(params.betas().orElseThrow())
        .anyMatch(beta -> beta.asString().equals(ClaudeInvoiceDraftModel.FALLBACK_BETA));
  }

  @Test
  void propertiesNeverPrintTheKey() {
    assertThat(props("sk-test-not-real").toString()).doesNotContain("sk-test-not-real");
  }
}
