package io.github.dimasnurfauzi.invoicing.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The JSON schema of a draft ({@code ai/invoice-draft.schema.json}), used twice: sent to Claude as
 * the structured-output format, and used here to validate what comes back (model output is
 * untrusted). Deliberately simple keywords only (types, required, additionalProperties), so the
 * same file is valid for both; value rules (decimals, ranges, dates) are checked by {@link
 * InvoiceDraftValidator}.
 */
@Component
public class InvoiceDraftSchema {

  static final String RESOURCE = "/ai/invoice-draft.schema.json";

  private final Map<String, Object> raw;
  private final Schema schema;

  public InvoiceDraftSchema(ObjectMapper objectMapper) {
    try (InputStream in = InvoiceDraftSchema.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + RESOURCE);
      }
      JsonNode node = objectMapper.readTree(in);
      this.raw = objectMapper.convertValue(node, new TypeReference<>() {});
      this.schema =
          SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
              .getSchema(node.toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The schema as a map, for the API request. */
  public Map<String, Object> asMap() {
    return raw;
  }

  /** Schema violations as readable messages; empty if valid. */
  public List<String> validate(JsonNode output) {
    return schema.validate(output).stream().map(Error::toString).toList();
  }
}
