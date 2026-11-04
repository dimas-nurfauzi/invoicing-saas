-- Payments against issued invoices (partial payments allowed). Tenant-owned.
-- Payments are append-only in this version: no update or delete, so PAID stays final.

CREATE TABLE payments (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    invoice_id  UUID          NOT NULL REFERENCES invoices (id),
    amount      NUMERIC(19,2) NOT NULL,
    paid_on     DATE          NOT NULL,
    method      VARCHAR(20)   NOT NULL,
    reference   VARCHAR(100),
    note        VARCHAR(500),
    -- No FK: the record keeps the user id even if that user is deleted later.
    recorded_by UUID          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method
        CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CARD', 'EWALLET', 'OTHER'))
);

CREATE INDEX idx_payments_tenant_id ON payments (tenant_id);
CREATE INDEX idx_payments_tenant_invoice ON payments (tenant_id, invoice_id);

ALTER TABLE invoices
    ADD COLUMN amount_paid NUMERIC(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN paid_at     TIMESTAMPTZ,
    -- Last line of defence against overpayment; the service checks first under a row lock.
    ADD CONSTRAINT ck_invoices_amount_paid CHECK (amount_paid >= 0 AND amount_paid <= total);
