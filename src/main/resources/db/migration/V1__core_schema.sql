-- ============================================================
-- Tessera — Core Schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS tessera;
SET search_path TO tessera, partman, public;

-- ── External Source Registry ──────────────────────────────────────────────
CREATE TABLE external_sources (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,
    source_type     TEXT NOT NULL,  -- POSTGIS | MYSQL | ORACLE | CASSANDRA | ELASTICSEARCH
    connection_key  TEXT NOT NULL,  -- references application.yml key
    is_active       BOOLEAN NOT NULL DEFAULT true,
    geometry_column TEXT,           -- hint for SQL sources
    geo_field       TEXT,           -- hint for NoSQL sources
    sync_strategy   TEXT NOT NULL DEFAULT 'POLL',  -- POLL | CDC | BATCH
    last_introspect TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Schema Metadata Cache ─────────────────────────────────────────────────
CREATE TABLE schema_metadata (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id      UUID NOT NULL REFERENCES external_sources(id) ON DELETE CASCADE,
    catalog_name   TEXT,
    schema_name    TEXT,
    table_name     TEXT NOT NULL,
    column_json    JSONB NOT NULL,
    row_estimate   BIGINT,
    has_geometry   BOOLEAN NOT NULL DEFAULT false,
    snapshotted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, catalog_name, schema_name, table_name)
);

CREATE INDEX idx_schema_meta_source ON schema_metadata(source_id);
CREATE INDEX idx_schema_meta_geo    ON schema_metadata(source_id) WHERE has_geometry;

-- ── Core Geospatial Features ──────────────────────────────────────────────
-- Partitioned by ingested_at — pg_partman manages ongoing partition creation.
-- NOTE: The table must exist before calling create_parent, but must NOT
--       have any data or child partitions yet.
CREATE TABLE geo_features (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    source_id     UUID NOT NULL,
    external_id   TEXT NOT NULL,
    source_table  TEXT NOT NULL,
    geometry      GEOMETRY(Geometry, 4326) NOT NULL,
    geometry_type TEXT NOT NULL,
    attributes    JSONB NOT NULL DEFAULT '{}',
    data_hash     BYTEA,
    ingested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_from    TIMESTAMPTZ,
    valid_to      TIMESTAMPTZ,
    PRIMARY KEY (id, ingested_at)
) PARTITION BY RANGE (ingested_at);

-- Hand pg_partman the parent table.
-- pg_partman 5.x API: p_type and p_template_table removed.
-- p_interval uses interval strings ('1 month', '1 week', etc.)
-- p_premake = number of future partitions to create in advance.
SELECT partman.create_parent(
               p_parent_table => 'tessera.geo_features',
               p_control      => 'ingested_at',
               p_interval     => '1 month',
               p_premake      => 3
       );

-- Configure this table in partman's maintenance metadata.
-- infinite_time_partitions keeps partman creating new ones indefinitely.
-- retention = NULL means we never auto-drop old partitions (manage manually).
UPDATE partman.part_config
SET    infinite_time_partitions = true,
       retention                = NULL,
       retention_keep_table     = true
WHERE  parent_table = 'tessera.geo_features';

-- ── Indexes — apply to parent, propagate to all partitions ───────────────
CREATE INDEX idx_geo_features_geom   ON geo_features USING GIST (geometry);
CREATE INDEX idx_geo_features_source ON geo_features (source_id, source_table);
CREATE INDEX idx_geo_features_extid  ON geo_features (external_id, source_id);
CREATE INDEX idx_geo_features_attrs  ON geo_features USING GIN (attributes jsonb_path_ops);
CREATE INDEX idx_geo_features_hash   ON geo_features (data_hash);
CREATE INDEX idx_geo_features_upd    ON geo_features (updated_at);

-- ── Sync Checkpoints ──────────────────────────────────────────────────────
CREATE TABLE sync_checkpoints (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id        UUID NOT NULL REFERENCES external_sources(id),
    table_name       TEXT NOT NULL,
    checkpoint_type  TEXT NOT NULL,  -- LSN | TIMESTAMP | OFFSET | CURSOR
    checkpoint_value TEXT NOT NULL,
    rows_processed   BIGINT NOT NULL DEFAULT 0,
    last_synced_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, table_name)
);