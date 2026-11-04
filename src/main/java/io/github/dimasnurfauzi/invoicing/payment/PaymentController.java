package io.github.dimasnurfauzi.invoicing.payment;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Payments of an invoice in the caller's tenant. ADMIN and STAFF may record and read. */
@RestController
@RequestMapping("/api/invoices/{invoiceId}/payments")
@Tag(name = "Payments")
public class PaymentController {

  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  /** Partial payments are fine; the invoice becomes PAID when its balance reaches zero. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RecordedPaymentResponse record(
      @PathVariable UUID invoiceId,
      @Valid @RequestBody PaymentRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return service.record(invoiceId, request, UUID.fromString(jwt.getSubject()));
  }

  @GetMapping
  public List<PaymentResponse> list(@PathVariable UUID invoiceId) {
    return service.list(invoiceId);
  }
}
