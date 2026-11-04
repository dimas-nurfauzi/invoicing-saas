package io.github.dimasnurfauzi.invoicing.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * {@link InvoiceDraftModel} on the Claude API (official Java SDK, beta messages endpoint):
 *
 * <ul>
 *   <li>structured output: {@code output_config.format} = the draft JSON schema, so the reply is
 *       JSON in that shape (still re-validated by the caller: output is untrusted);
 *   <li>effort from config (default {@code low}: a short extraction task);
 *   <li>server-side refusal fallback ({@code fallbacks: "default"}): if a safety classifier
 *       declines, the API retries on a fallback model before we see a refusal;
 *   <li>SDK retries 429/5xx/connection errors twice; request timeout from config.
 * </ul>
 *
 * The client is created only when an API key is configured.
 */
@Component
public class ClaudeInvoiceDraftModel implements InvoiceDraftModel {

  static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  private final AiProperties props;
  private final BetaJsonOutputFormat format;
  private final AnthropicClient client;

  public ClaudeInvoiceDraftModel(AiProperties props, InvoiceDraftSchema schema) {
    this.props = props;
    BetaJsonOutputFormat.Schema.Builder schemaBuilder = BetaJsonOutputFormat.Schema.builder();
    schema
        .asMap()
        .forEach((key, value) -> schemaBuilder.putAdditionalProperty(key, JsonValue.from(value)));
    this.format = BetaJsonOutputFormat.builder().schema(schemaBuilder.build()).build();
    this.client =
        props.apiKey().isEmpty()
            ? null
            : AnthropicOkHttpClient.builder()
                .apiKey(props.apiKey())
                .timeout(props.timeout())
                .maxRetries(2)
                .build();
  }

  @Override
  public boolean isConfigured() {
    return client != null;
  }

  @Override
  public Reply complete(String system, String user) {
    if (client == null) {
      throw new AiNotConfiguredException();
    }
    try {
      BetaMessage message = client.beta().messages().create(params(system, user));
      String text =
          message.content().stream()
              .flatMap(block -> block.text().stream())
              .map(BetaTextBlock::text)
              .collect(Collectors.joining());
      boolean refused = message.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false);
      boolean truncated = message.stopReason().map(BetaStopReason.MAX_TOKENS::equals).orElse(false);
      return new Reply(
          text, refused, truncated, message.usage().inputTokens(), message.usage().outputTokens());
    } catch (AnthropicException e) {
      throw new AiUnavailableException(e);
    }
  }

  MessageCreateParams params(String system, String user) {
    return MessageCreateParams.builder()
        .model(props.model())
        .maxTokens(props.maxTokens())
        .system(system)
        .addUserMessage(user)
        .outputConfig(
            BetaOutputConfig.builder()
                .effort(BetaOutputConfig.Effort.of(props.effort()))
                .format(format)
                .build())
        .addBeta(FALLBACK_BETA)
        .fallbacksDefault()
        .build();
  }
}
