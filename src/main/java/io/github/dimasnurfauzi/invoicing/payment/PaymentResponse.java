package io.github.dimasnurfauzi.invoicing.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
    LocalDate paidOn,
    PaymentMethod method,
    String reference,
    String note,
    UUID recordedBy,
    Instant createdAt) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getAmount(),
        payment.getPaidOn(),
        payment.getMethod(),
        payment.getReference(),
        payment.getNote(),
        payment.getRecordedBy(),
        payment.getCreatedAt());
  }
}
