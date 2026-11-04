package io.github.dimasnurfauzi.invoicing.invoice;

import io.github.dimasnurfauzi.invoicing.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Invoices of the caller's tenant. ADMIN and STAFF manage drafts and issue; only ADMIN voids. */
@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

  private final InvoiceService service;
  private final InvoiceIssueService issueService;

  public InvoiceController(InvoiceService service, InvoiceIssueService issueService) {
    this.service = service;
    this.issueService = issueService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
    return service.create(request);
  }

  @GetMapping
  public PageResponse<InvoiceSummaryResponse> list(
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(status, customerId, page, size);
  }

  @GetMapping("/{id}")
  public InvoiceResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  /** Replaces the whole draft, lines included. Only DRAFT invoices can be edited. */
  @PutMapping("/{id}")
  public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest request) {
    return service.update(id, request);
  }

  /** Deletes a DRAFT permanently. Issued invoices can't be deleted. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  /** DRAFT → SENT: assigns the next INV-<year>-NNNN number and freezes the bill-to data. */
  @PostMapping("/{id}/issue")
  public InvoiceResponse issue(@PathVariable UUID id) {
    return issueService.issue(id);
  }

  /** SENT/OVERDUE → VOID with a reason. The number stays used. ADMIN only. */
  @PostMapping("/{id}/void")
  @PreAuthorize("hasRole('ADMIN')")
  public InvoiceResponse voidInvoice(
      @PathVariable UUID id, @Valid @RequestBody VoidRequest request) {
    return issueService.voidInvoice(id, request.reason());
  }
}
