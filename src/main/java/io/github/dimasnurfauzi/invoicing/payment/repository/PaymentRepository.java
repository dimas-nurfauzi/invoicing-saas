package io.github.dimasnurfauzi.invoicing.payment.repository;

import io.github.dimasnurfauzi.invoicing.payment.entity.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped to the current tenant by {@code @TenantId}; no method takes a tenant id. */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  List<Payment> findByInvoiceIdOrderByPaidOnAscCreatedAtAsc(UUID invoiceId);
}
