package io.github.dimasnurfauzi.invoicing.invoice.service;

import io.github.dimasnurfauzi.invoicing.common.dto.PageResponse;
import io.github.dimasnurfauzi.invoicing.common.exception.ConflictException;
import io.github.dimasnurfauzi.invoicing.common.exception.NotFoundException;
import io.github.dimasnurfauzi.invoicing.customer.entity.Customer;
import io.github.dimasnurfauzi.invoicing.customer.repository.CustomerRepository;
import io.github.dimasnurfauzi.invoicing.invoice.domain.InvoiceLineDraft;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceRequest;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceResponse;
import io.github.dimasnurfauzi.invoicing.invoice.dto.InvoiceSummaryResponse;
import io.github.dimasnurfauzi.invoicing.invoice.entity.Invoice;
import io.github.dimasnurfauzi.invoicing.invoice.entity.InvoiceStatus;
import io.github.dimasnurfauzi.invoicing.invoice.repository.InvoiceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft invoices of the caller's tenant. No method takes a tenant id: invoices and the referenced
 * customer are both looked up through the tenant filter, so another tenant's invoice or customer is
 * "not found".
 */
@Service
public class InvoiceService {

  public static final String DEFAULT_CURRENCY = "IDR";

  /** Newest issue date first; id breaks ties so paging is stable. */
  public static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

  private final InvoiceRepository invoices;
  private final CustomerRepository customers;

  public InvoiceService(InvoiceRepository invoices, CustomerRepository customers) {
    this.invoices = invoices;
    this.customers = customers;
  }

  @Transactional
  public InvoiceResponse create(InvoiceRequest request) {
    Invoice invoice =
        new Invoice(
            billableCustomer(request.customerId()),
            currency(request),
            request.issueDate(),
            request.dueDate(),
            blankToNull(request.notes()),
            drafts(request));
    return InvoiceResponse.from(invoices.saveAndFlush(invoice));
  }

  @Transactional(readOnly = true)
  public PageResponse<InvoiceSummaryResponse> list(
      InvoiceStatus status, UUID customerId, int page, int size) {
    Specification<Invoice> spec = (root, query, cb) -> cb.conjunction();
    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (customerId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
    }
    return PageResponse.from(
        invoices.findAll(spec, PageRequest.of(page, size, DEFAULT_SORT)),
        InvoiceSummaryResponse::from);
  }

  @Transactional(readOnly = true)
  public InvoiceResponse get(UUID id) {
    return InvoiceResponse.from(find(id));
  }

  @Transactional
  public InvoiceResponse update(UUID id, InvoiceRequest request) {
    Invoice invoice = invoices.lockById(id).orElseThrow(() -> new NotFoundException("Invoice"));
    requireDraft(invoice, "edited");
    invoice.replaceDraft(
        billableCustomer(request.customerId()),
        currency(request),
        request.issueDate(),
        request.dueDate(),
        blankToNull(request.notes()),
        drafts(request));
    return InvoiceResponse.from(invoices.saveAndFlush(invoice));
  }

  /** Drafts have no number and nothing refers to them yet, so they are really deleted. */
  @Transactional
  public void delete(UUID id) {
    Invoice invoice = invoices.lockById(id).orElseThrow(() -> new NotFoundException("Invoice"));
    requireDraft(invoice, "deleted");
    invoices.delete(invoice);
  }

  private Invoice find(UUID id) {
    return invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice"));
  }

  /** Tenant-scoped like every lookup: another tenant's customer id is "not found" (404). */
  private Customer billableCustomer(UUID customerId) {
    Customer customer =
        customers.findById(customerId).orElseThrow(() -> new NotFoundException("Customer"));
    if (customer.isArchived()) {
      throw new ConflictException("Customer is archived; restore it before invoicing");
    }
    return customer;
  }

  private static void requireDraft(Invoice invoice, String action) {
    if (!invoice.isDraft()) {
      throw new ConflictException("Only draft invoices can be " + action);
    }
  }

  private static List<InvoiceLineDraft> drafts(InvoiceRequest request) {
    return request.lines().stream().map(InvoiceRequest.Line::toDraft).toList();
  }

  private static String currency(InvoiceRequest request) {
    return request.currency() == null ? DEFAULT_CURRENCY : request.currency();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
