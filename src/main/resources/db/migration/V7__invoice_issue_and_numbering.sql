-- Issuing invoices: gapless numbers per (tenant, year), bill-to snapshot, void.

-- One row per tenant and year; issuing locks it (SELECT ... FOR UPDATE) and increments
-- last_value in the same transaction that marks the invoice SENT, so a rollback also rolls back
-- the number (no gaps) and concurrent issues are serialized (no duplicates). Tenant-owned.
CREATE TABLE invoice_number_counters (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID    NOT NULL REFERENCES tenants (id),
    number_year INT     NOT NULL,
    last_value  INT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoice_number_counters UNIQUE (tenant_id, number_year),
    CONSTRAINT ck_invoice_number_counters_value CHECK (last_value >= 0)
);

-- The unique constraint's index leads with tenant_id; this one is the explicit per-table index.
CREATE INDEX idx_invoice_number_counters_tenant_id ON invoice_number_counters (tenant_id);

ALTER TABLE invoices
    ADD COLUMN issued_at       TIMESTAMPTZ,
    ADD COLUMN voided_at       TIMESTAMPTZ,
    ADD COLUMN void_reason     VARCHAR(500),
    -- Snapshot of the customer at issue time: later customer edits don't change sent invoices.
    ADD COLUMN bill_to_name    VARCHAR(200),
    ADD COLUMN bill_to_email   VARCHAR(254),
    ADD COLUMN bill_to_address VARCHAR(500),
    ADD COLUMN bill_to_tax_id  VARCHAR(30),
    ADD CONSTRAINT ck_invoices_number_when_issued
        CHECK ((status = 'DRAFT') = (invoice_number IS NULL));

CREATE UNIQUE INDEX uq_invoices_tenant_number
    ON invoices (tenant_id, invoice_number) WHERE invoice_number IS NOT NULL;
