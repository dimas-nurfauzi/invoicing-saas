-- Platform administrators (the SaaS operator, not a tenant) and tenant suspension.
-- platform_admins is deliberately not tenant-owned: no tenant_id.

CREATE TABLE platform_admins (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_platform_admins_email UNIQUE (email)
);

ALTER TABLE tenants
    ADD COLUMN status       VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN suspended_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
