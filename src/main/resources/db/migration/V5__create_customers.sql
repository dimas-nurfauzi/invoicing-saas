-- Customers of a tenant (who it bills). Tenant-owned: tenant_id NOT NULL + index.
-- Deleting a customer archives it (archived_at) so invoices can keep pointing at it.

CREATE TABLE customers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(254),
    phone           VARCHAR(30),
    billing_address VARCHAR(500),
    tax_id          VARCHAR(30),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE INDEX idx_customers_tenant_id ON customers (tenant_id);
-- Search and default ordering are by name within a tenant.
CREATE INDEX idx_customers_tenant_name ON customers (tenant_id, lower(name));
