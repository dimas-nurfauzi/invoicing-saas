-- Baseline migration. Feature tables arrive in later migrations (V2+).
-- pgcrypto provides gen_random_uuid() for UUID primary keys.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
