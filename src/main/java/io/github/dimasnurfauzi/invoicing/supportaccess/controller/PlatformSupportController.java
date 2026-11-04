package io.github.dimasnurfauzi.invoicing.supportaccess.controller;

import io.github.dimasnurfauzi.invoicing.common.dto.PageResponse;
import io.github.dimasnurfauzi.invoicing.customer.dto.CustomerResponse;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceResponse;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceSummaryResponse;
import io.github.dimasnurfauzi.invoicing.payment.dto.PaymentResponse;
import io.github.dimasnurfauzi.invoicing.supportaccess.service.PlatformSupportService;
import io.github.dimasnurfauzi.invoicing.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only support views of a tenant's data, for platform admins (enforced by the platform
 * security chain). Each call needs a reason (e.g. a ticket number), an active grant from the
 * tenant, and is logged for the tenant to see.
 */
@RestController
@RequestMapping("/api/platform/tenants/{tenantId}/support")
@Tag(name = "Platform admin")
public class PlatformSupportController {

  private final PlatformSupportService service;

  public PlatformSupportController(PlatformSupportService service) {
    this.service = service;
  }

  @GetMapping("/users")
  public List<UserResponse> users(
      @PathVariable UUID tenantId,
      @RequestParam @NotBlank @Size(min = 3, max = 200) String reason,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.listUsers(tenantId, UUID.fromString(jwt.getSubject()), reason.trim());
  }

  @GetMapping("/customers")
  public PageResponse<CustomerResponse> customers(
      @PathVariable UUID tenantId,
      @RequestParam @NotBlank @Size(min = 3, max = 200) String reason,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.listCustomers(
        tenantId, UUID.fromString(jwt.getSubject()), reason.trim(), page, size);
  }

  @GetMapping("/invoices")
  public PageResponse<InvoiceSummaryResponse> invoices(
      @PathVariable UUID tenantId,
      @RequestParam @NotBlank @Size(min = 3, max = 200) String reason,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.listInvoices(
        tenantId, UUID.fromString(jwt.getSubject()), reason.trim(), page, size);
  }

  @GetMapping("/invoices/{invoiceId}")
  public InvoiceResponse invoice(
      @PathVariable UUID tenantId,
      @PathVariable UUID invoiceId,
      @RequestParam @NotBlank @Size(min = 3, max = 200) String reason,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.getInvoice(
        tenantId, UUID.fromString(jwt.getSubject()), reason.trim(), invoiceId);
  }

  @GetMapping("/invoices/{invoiceId}/payments")
  public List<PaymentResponse> payments(
      @PathVariable UUID tenantId,
      @PathVariable UUID invoiceId,
      @RequestParam @NotBlank @Size(min = 3, max = 200) String reason,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.listPayments(
        tenantId, UUID.fromString(jwt.getSubject()), reason.trim(), invoiceId);
  }
}
