SET search_path TO tessera, public;

CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    key_prefix      TEXT NOT NULL,           -- first 8 chars of raw key for lookup
    key_hash        TEXT NOT NULL UNIQUE,     -- SHA-256 hex of full raw key
    owner           TEXT NOT NULL,            -- username who created it
    scopes          TEXT[] NOT NULL DEFAULT ARRAY['QUERY_READ','QUERY_EXECUTE'],
    rate_limit_rpm  INT NOT NULL DEFAULT 60,  -- requests per minute
    is_active       BOOLEAN NOT NULL DEFAULT true,
    last_used_at    TIMESTAMPTZ,
    request_count   BIGINT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ,             -- NULL = never expires
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix) WHERE is_active = true;
CREATE INDEX idx_api_keys_owner  ON api_keys(owner);