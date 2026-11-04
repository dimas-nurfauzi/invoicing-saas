package io.github.dimasnurfauzi.invoicing.summary;

import io.github.dimasnurfauzi.invoicing.invoice.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only aggregates over invoices. JPQL, so the tenant filter applies like everywhere else. */
public interface SummaryRepository extends Repository<Invoice, UUID> {

  /** One row per (currency, status) with invoices issued in [from, to]. */
  @Query(
      """
      SELECT new io.github.dimasnurfauzi.invoicing.summary.StatusTotals(
          i.currency, i.status, count(i), sum(i.total), sum(i.amountPaid))
      FROM Invoice i
      WHERE i.issueDate >= :from AND i.issueDate <= :to
      GROUP BY i.currency, i.status
      ORDER BY i.currency
      """)
  List<StatusTotals> totalsByCurrencyAndStatus(
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
