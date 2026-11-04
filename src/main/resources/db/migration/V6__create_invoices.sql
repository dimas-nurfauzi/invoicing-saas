-- Invoices and their line items. Both tables are tenant-owned (tenant_id NOT NULL + index).
-- Amounts NUMERIC(19,2), percentages NUMERIC(5,2) (CLAUDE.md). quantity is NUMERIC(19,3)
-- (fractional units such as 1.5 kg or 2.25 hours); every amount is rounded HALF_UP to 2 decimals
-- per line and then summed, so stored totals always equal the sum of stored line amounts.

CREATE TABLE invoices (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenants (id),
    customer_id    UUID          NOT NULL REFERENCES customers (id),
    status         VARCHAR(10)   NOT NULL DEFAULT 'DRAFT',
    -- Assigned when the invoice is issued (later PR); drafts have no number.
    invoice_number VARCHAR(20),
    currency       VARCHAR(3)    NOT NULL DEFAULT 'IDR',
    issue_date     DATE          NOT NULL,
    due_date       DATE          NOT NULL,
    notes          VARCHAR(2000),
    subtotal       NUMERIC(19,2) NOT NULL,
    discount_total NUMERIC(19,2) NOT NULL,
    tax_total      NUMERIC(19,2) NOT NULL,
    total          NUMERIC(19,2) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'VOID')),
    CONSTRAINT ck_invoices_dates CHECK (due_date >= issue_date),
    CONSTRAINT ck_invoices_amounts
        CHECK (subtotal >= 0 AND discount_total >= 0 AND tax_total >= 0 AND total >= 0)
);

CREATE INDEX idx_invoices_tenant_id ON invoices (tenant_id);
CREATE INDEX idx_invoices_tenant_status ON invoices (tenant_id, status);
CREATE INDEX idx_invoices_tenant_customer ON invoices (tenant_id, customer_id);

CREATE TABLE invoice_lines (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenants (id),
    invoice_id       UUID          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    line_no          INT           NOT NULL,
    description      VARCHAR(500)  NOT NULL,
    quantity         NUMERIC(19,3) NOT NULL,
    unit_price       NUMERIC(19,2) NOT NULL,
    discount_percent NUMERIC(5,2)  NOT NULL,
    tax_rate_percent NUMERIC(5,2)  NOT NULL,
    line_subtotal    NUMERIC(19,2) NOT NULL,
    line_discount    NUMERIC(19,2) NOT NULL,
    line_tax         NUMERIC(19,2) NOT NULL,
    line_total       NUMERIC(19,2) NOT NULL,
    CONSTRAINT ck_invoice_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_lines_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_invoice_lines_discount CHECK (discount_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_invoice_lines_tax CHECK (tax_rate_percent BETWEEN 0 AND 100)
);

CREATE INDEX idx_invoice_lines_tenant_id ON invoice_lines (tenant_id);
CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines (invoice_id);
