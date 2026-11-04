package io.github.dimasnurfauzi.invoicing.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dimasnurfauzi.invoicing.customer.Customer;
import io.github.dimasnurfauzi.invoicing.customer.CustomerRepository;
import io.github.dimasnurfauzi.invoicing.invoice.InvoiceCalculator;
import io.github.dimasnurfauzi.invoicing.invoice.InvoiceRequest;
import io.github.dimasnurfauzi.invoicing.invoice.InvoiceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Free text → draft invoice, never saved. Claude output is untrusted, so each attempt is checked in
 * three steps: parse as JSON → {@link InvoiceDraftSchema} → {@link InvoiceDraftValidator} (same
 * line rules as manual entry). A failed attempt is retried with the errors in the prompt, at most
 * {@code app.ai.max-attempts} times in total (default 3); then 502.
 *
 * <p>The customer name is matched locally against the caller's tenant (tenant-filtered query);
 * customer data is never sent to the model. The request text is never logged.
 */
@Service
public class AiDraftService {

  static final int DEFAULT_DUE_DAYS = 30;
  static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Jakarta");

  private static final Logger log = LoggerFactory.getLogger(AiDraftService.class);

  private final InvoiceDraftModel model;
  private final InvoiceDraftSchema schema;
  private final InvoiceDraftValidator validator;
  private final CustomerRepository customers;
  private final ObjectMapper objectMapper;
  private final AiProperties props;
  private final Clock clock;

  public AiDraftService(
      InvoiceDraftModel model,
      InvoiceDraftSchema schema,
      InvoiceDraftValidator validator,
      CustomerRepository customers,
      ObjectMapper objectMapper,
      AiProperties props) {
    this.model = model;
    this.schema = schema;
    this.validator = validator;
    this.customers = customers;
    this.objectMapper = objectMapper;
    this.props = props;
    this.clock = Clock.system(BUSINESS_ZONE);
  }

  /** Not {@code @Transactional}: no database work happens while waiting for the model. */
  public AiDraftResponse draft(String text) {
    if (!model.isConfigured()) {
      throw new AiNotConfiguredException();
    }
    LocalDate today = LocalDate.now(clock);
    List<String> previousErrors = List.of();
    for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
      InvoiceDraftModel.Reply reply =
          model.complete(
              InvoiceDraftPrompt.SYSTEM, InvoiceDraftPrompt.user(text, today, previousErrors));
      log.info(
          "AI draft attempt {}: {} chars in, {} input / {} output tokens, refused={}, truncated={}",
          attempt,
          text.length(),
          reply.inputTokens(),
          reply.outputTokens(),
          reply.refused(),
          reply.truncated());
      if (reply.refused()) {
        throw new AiRefusedException();
      }
      List<String> errors = new ArrayList<>();
      ExtractedDraft extracted = parse(reply, errors);
      InvoiceDraftValidator.Result checked =
          extracted == null ? null : validator.validate(extracted);
      if (checked != null) {
        errors.addAll(checked.errors());
      }
      if (errors.isEmpty()) {
        return toResponse(extracted, checked.lines(), today, attempt);
      }
      log.info("AI draft attempt {} rejected: {} problem(s)", attempt, errors.size());
      previousErrors = errors;
    }
    throw new AiInvalidOutputException(props.maxAttempts());
  }

  private ExtractedDraft parse(InvoiceDraftModel.Reply reply, List<String> errors) {
    if (reply.truncated()) {
      errors.add("output was cut off; keep it shorter");
      return null;
    }
    JsonNode node;
    try {
      node = objectMapper.readTree(reply.text());
    } catch (JsonProcessingException e) {
      errors.add("output was not valid JSON");
      return null;
    }
    if (node == null || node.isMissingNode()) {
      errors.add("output was empty");
      return null;
    }
    List<String> violations = schema.validate(node);
    if (!violations.isEmpty()) {
      errors.addAll(violations);
      return null;
    }
    return objectMapper.convertValue(node, ExtractedDraft.class);
  }

  private AiDraftResponse toResponse(
      ExtractedDraft draft, List<InvoiceRequest.Line> lines, LocalDate today, int attempts) {
    List<String> warnings = new ArrayList<>();
    UUID customerId = matchCustomer(draft.customerName().trim(), warnings);

    LocalDate issueDate =
        blank(draft.issueDate()) ? null : LocalDate.parse(draft.issueDate().trim());
    if (issueDate == null) {
      issueDate = today;
      warnings.add("No invoice date in the request; using today (" + today + ")");
    }
    LocalDate dueDate = blank(draft.dueDate()) ? null : LocalDate.parse(draft.dueDate().trim());
    if (dueDate == null) {
      dueDate = issueDate.plusDays(DEFAULT_DUE_DAYS);
      warnings.add(
          "No due date in the request; using " + DEFAULT_DUE_DAYS + " days after the invoice date");
    }
    String currency = draft.currency().trim();
    if (currency.isEmpty()) {
      currency = InvoiceService.DEFAULT_CURRENCY;
      warnings.add("No currency in the request; assuming " + currency);
    }

    BigDecimal previewTotal =
        InvoiceCalculator.totals(
                lines.stream()
                    .map(l ->
                        InvoiceCalculator.line(
                            l.quantity(), l.unitPrice(), l.discountPercent(), l.taxRatePercent()))
                    .toList())
            .total();
    return new AiDraftResponse(
        customerId,
        draft.customerName().trim(),
        currency,
        issueDate,
        dueDate,
        blank(draft.notes()) ? null : draft.notes().trim(),
        lines.stream().map(AiDraftResponse.Line::from).toList(),
        previewTotal,
        warnings,
        attempts);
  }

  /** Tenant-filtered exact match; ambiguous or missing → null plus a warning. */
  private UUID matchCustomer(String name, List<String> warnings) {
    if (name.isEmpty()) {
      warnings.add("No customer in the request; pick one before saving");
      return null;
    }
    List<Customer> matches = customers.findByNameIgnoreCaseAndArchivedAtIsNull(name);
    if (matches.size() == 1) {
      return matches.get(0).getId();
    }
    warnings.add(
        matches.isEmpty()
            ? "No customer named \"" + name + "\"; create it or pick one before saving"
            : "Several customers are named \"" + name + "\"; pick one before saving");
    return null;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
