package io.github.dimasnurfauzi.invoicing.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Last invoice number used by the current tenant in one year. Tenant-owned. */
@Entity
@Table(name = "invoice_number_counters")
public class InvoiceNumberCounter {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "number_year", nullable = false, updatable = false)
  private int numberYear;

  @Column(name = "last_value", nullable = false)
  private int lastValue;

  protected InvoiceNumberCounter() {}

  InvoiceNumberCounter(int numberYear) {
    this.numberYear = numberYear;
  }

  /** Call only while holding the row lock. */
  int next() {
    lastValue++;
    return lastValue;
  }

  public int getNumberYear() {
    return numberYear;
  }

  public int getLastValue() {
    return lastValue;
  }
}
