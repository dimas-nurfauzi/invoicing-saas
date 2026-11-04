package io.github.dimasnurfauzi.invoicing.ai;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AI-assisted invoice drafting for the caller's tenant (any role). Nothing is saved. */
@RestController
@Tag(name = "AI drafts")
public class AiDraftController {

  private final AiDraftService service;

  public AiDraftController(AiDraftService service) {
    this.service = service;
  }

  /**
   * Turns a free-text billing request into a draft. Review it, then send customerId, currency,
   * dates, notes and lines to {@code POST /api/invoices} to save it.
   */
  @PostMapping("/api/invoices/ai-draft")
  public AiDraftResponse draft(@Valid @RequestBody AiDraftRequest request) {
    return service.draft(request.text());
  }
}
