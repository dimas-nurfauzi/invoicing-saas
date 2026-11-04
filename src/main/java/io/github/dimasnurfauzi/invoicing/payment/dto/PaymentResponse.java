package io.github.dimasnurfauzi.invoicing.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.dimasnurfauzi.invoicing.payment.entity.Payment;
import io.github.dimasnurfauzi.invoicing.payment.entity.PaymentMethod;
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
