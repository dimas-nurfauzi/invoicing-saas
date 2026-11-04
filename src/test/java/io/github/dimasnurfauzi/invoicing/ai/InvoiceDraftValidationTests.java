package io.github.dimasnurfauzi.invoicing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Schema and value checks on model output, without Spring or the network. */
class InvoiceDraftValidationTests {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InvoiceDraftSchema schema = new InvoiceDraftSchema(mapper);
  private final InvoiceDraftValidator validator =
      new InvoiceDraftValidator(Validation.buildDefaultValidatorFactory().getValidator());

  private static ExtractedDraft.Line line(String qty, String price, String discount, String tax) {
    return new ExtractedDraft.Line("Item", qty, price, discount, tax);
  }

  private static ExtractedDraft draft(List<ExtractedDraft.Line> lines) {
    return new ExtractedDraft("PT X", "2026-11-04", "2026-12-04", "IDR", "", lines);
  }

  @Test
  void schemaAcceptsAWellFormedDraft() throws Exception {
    String json =
        """
        {"customerName": "", "issueDate": "", "dueDate": "", "currency": "", "notes": "",
         "lines": [{"description": "a", "quantity": "1", "unitPrice": "1",
                    "discountPercent": "0", "taxRatePercent": "0"}]}
        """;

    assertThat(schema.validate(mapper.readTree(json))).isEmpty();
  }

  @Test
  void schemaRejectsMissingWrongTypedAndExtraFields() throws Exception {
    assertThat(schema.validate(mapper.readTree("{\"customerName\": \"X\"}"))).isNotEmpty();
    assertThat(
            schema.validate(
                mapper.readTree(
                    """
                    {"customerName": "", "issueDate": "", "dueDate": "", "currency": "",
                     "notes": "", "lines": [{"description": "a", "quantity": 1, "unitPrice": "1",
                     "discountPercent": "0", "taxRatePercent": "0"}]}
                    """)))
        .isNotEmpty();
    assertThat(
            schema.validate(
                mapper.readTree(
                    """
                    {"customerName": "", "issueDate": "", "dueDate": "", "currency": "",
                     "notes": "", "lines": [], "tenantId": "x"}
                    """)))
        .isNotEmpty();
  }

  @Test
  void schemaFileIsWhatWeSendToTheModel() {
    assertThat(schema.asMap())
        .containsEntry("type", "object")
        .containsEntry("additionalProperties", false)
        .containsKey("required");
  }

  @Test
  void validatorAppliesTheManualEntryLineRules() {
    assertThat(validator.validate(draft(List.of(line("1.5", "1500000", "0", "11")))).errors())
        .isEmpty();
    assertThat(validator.validate(draft(List.of(line("0", "1", "0", "0")))).errors())
        .anyMatch(e -> e.startsWith("lines[0].quantity"));
    assertThat(validator.validate(draft(List.of(line("1.0001", "1", "0", "0")))).errors())
        .anyMatch(e -> e.startsWith("lines[0].quantity"));
    assertThat(validator.validate(draft(List.of(line("1", "1.234", "0", "0")))).errors())
        .anyMatch(e -> e.startsWith("lines[0].unitPrice"));
    assertThat(validator.validate(draft(List.of(line("1", "1,5jt", "0", "0")))).errors())
        .anyMatch(e -> e.contains("plain decimal"));
    assertThat(validator.validate(draft(List.of(line("1", "1", "0", "101")))).errors())
        .anyMatch(e -> e.startsWith("lines[0].taxRatePercent"));
  }

  @Test
  void validatorChecksDatesCurrencyAndLineCount() {
    assertThat(validator.validate(draft(List.of())).errors()).anyMatch(e -> e.startsWith("lines"));
    assertThat(
            validator
                .validate(
                    new ExtractedDraft(
                        "X",
                        "2026-12-04",
                        "2026-11-04",
                        "idr",
                        "",
                        List.of(line("1", "1", "0", "0"))))
                .errors())
        .anyMatch(e -> e.startsWith("dueDate"))
        .anyMatch(e -> e.startsWith("currency"));
    assertThat(
            validator
                .validate(
                    new ExtractedDraft(
                        "X", "04/11/2026", "", "", "", List.of(line("1", "1", "0", "0"))))
                .errors())
        .anyMatch(e -> e.startsWith("issueDate"));
  }

  @Test
  void blankPercentagesMeanZero() {
    InvoiceDraftValidator.Result result =
        validator.validate(draft(List.of(line("1", "1", "", ""))));

    assertThat(result.errors()).isEmpty();
    assertThat(result.lines().get(0).taxRatePercent()).isZero();
  }
}
