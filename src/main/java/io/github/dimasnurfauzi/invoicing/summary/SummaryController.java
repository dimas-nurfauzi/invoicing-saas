package io.github.dimasnurfauzi.invoicing.summary;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Billing summary of the caller's tenant. Any role. */
@RestController
@RequestMapping("/api/summary")
@Tag(name = "Summary")
public class SummaryController {

  private final SummaryService service;

  public SummaryController(SummaryService service) {
    this.service = service;
  }

  /** Optional {@code from} / {@code to} (ISO dates, inclusive) filter on the issue date. */
  @GetMapping
  public SummaryResponse summary(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.summary(from, to);
  }
}
