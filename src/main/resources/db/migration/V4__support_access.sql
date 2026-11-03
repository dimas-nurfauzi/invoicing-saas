-- Support access: a tenant ADMIN grants the SaaS operator time-limited, read-only access to the
-- tenant's data; every read is logged. Both tables are tenant-owned (tenant_id NOT NULL + index),
-- so tenants see only their own grants and logs through the normal tenant filter.

CREATE TABLE support_access_grants (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    -- No FK: the audit trail keeps the granting user's id even if that user is deleted later.
    granted_by UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_support_access_grants_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_support_access_grants_tenant_id ON support_access_grants (tenant_id);

CREATE TABLE support_access_logs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenants (id),
    grant_id          UUID         NOT NULL REFERENCES support_access_grants (id),
    platform_admin_id UUID         NOT NULL REFERENCES platform_admins (id),
    reason            VARCHAR(200) NOT NULL,
    resource          VARCHAR(100) NOT NULL,
    accessed_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_support_access_logs_tenant_id ON support_access_logs (tenant_id);
