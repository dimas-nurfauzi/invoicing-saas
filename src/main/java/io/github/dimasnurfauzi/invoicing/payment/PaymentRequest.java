package io.github.dimasnurfauzi.invoicing.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Money may be sent as a JSON string or number. No tenant id. */
public record PaymentRequest(
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2)
        BigDecimal amount,
    @NotNull @PastOrPresent LocalDate paidOn,
    @NotNull PaymentMethod method,
    @Size(max = 100) String reference,
    @Size(max = 500) String note) {}
