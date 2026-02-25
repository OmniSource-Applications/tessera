SET search_path TO tessera, public;

-- ── Query Catalog ─────────────────────────────────────────────────────────
CREATE TABLE query_catalog (
                               id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               name          TEXT NOT NULL UNIQUE,
                               description   TEXT,
                               category      TEXT NOT NULL,
                               query_sql     TEXT NOT NULL,
                               param_schema  JSONB,
                               result_schema JSONB,
                               timeout_ms    INT NOT NULL DEFAULT 30000,
                               is_streaming  BOOLEAN NOT NULL DEFAULT false,
                               cache_ttl_sec INT,
                               tags          TEXT[],
                               created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_category ON query_catalog(category);
CREATE INDEX idx_catalog_tags     ON query_catalog USING GIN(tags);

-- ── Seed: Built-in Prepared Statements ───────────────────────────────────
INSERT INTO query_catalog (name, description, category, query_sql, param_schema, is_streaming, tags) VALUES

    ('features.by_bbox',
    'Fetch all geo features within a bounding box',
    'STATIC',
    'SELECT f.id, f.external_id, f.source_table,
          ST_AsGeoJSON(f.geometry)::jsonb AS geometry,
          f.attributes, f.updated_at
    FROM tessera.geo_features f
    WHERE ST_Intersects(f.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
     AND (:sourceId IS NULL OR f.source_id = :sourceId::uuid)
    ORDER BY f.updated_at DESC
    LIMIT :limit OFFSET :offset',
    '{"type":"object","properties":{"minLon":{"type":"number"},"minLat":{"type":"number"},
    "maxLon":{"type":"number"},"maxLat":{"type":"number"},
    "sourceId":{"type":"string","nullable":true},
    "limit":{"type":"integer","default":1000},
    "offset":{"type":"integer","default":0}}}'::jsonb,
    false,
    ARRAY['spatial','bbox']),

    ('features.within_radius',
    'Features within radius (meters) of a point',
    'STATIC',
    'SELECT f.id, f.external_id, f.source_table,
          ST_AsGeoJSON(f.geometry)::jsonb AS geometry,
          ST_Distance(f.geometry::geography, ST_Point(:lon, :lat, 4326)::geography) AS distance_m,
          f.attributes
    FROM tessera.geo_features f
    WHERE ST_DWithin(f.geometry::geography, ST_Point(:lon, :lat, 4326)::geography, :radiusMeters)
    ORDER BY distance_m
    LIMIT :limit',
    '{"type":"object","properties":{"lon":{"type":"number"},"lat":{"type":"number"},
    "radiusMeters":{"type":"number"},
    "limit":{"type":"integer","default":500}}}'::jsonb,
    false,
    ARRAY['spatial','radius']),

    ('h3.cell_features',
    'All features within a given H3 cell at a specific resolution',
    'H3',
    'SELECT f.id, f.external_id, f.source_table,
          ST_AsGeoJSON(f.geometry)::jsonb AS geometry, f.attributes
    FROM tessera.h3_cell_index h
    JOIN tessera.geo_features f ON f.id = h.feature_id AND f.ingested_at = h.feature_ingest
    WHERE h.resolution = :resolution
     AND h.h3_index = :h3CellAddress::h3index
    ORDER BY f.updated_at DESC',
    '{"type":"object","properties":{"h3CellAddress":{"type":"string"},
    "resolution":{"type":"integer","minimum":0,"maximum":15}}}'::jsonb,
    false,
    ARRAY['h3','cell']),

    ('h3.density_grid',
    'Feature density grid across H3 cells intersecting a bbox (from materialized view)',
    'H3',
    'SELECT h3_index::text, feature_count,
          ST_AsGeoJSON(hull_geom)::jsonb AS hull, last_updated
    FROM tessera.mv_h3_density_r7
    WHERE hull_geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
    ORDER BY feature_count DESC
    LIMIT :limit',
    '{"type":"object","properties":{"minLon":{"type":"number"},"minLat":{"type":"number"},
    "maxLon":{"type":"number"},"maxLat":{"type":"number"},
    "limit":{"type":"integer","default":5000}}}'::jsonb,
    false,
    ARRAY['h3','density','aggregate']),

    ('h3.k_ring_features',
    'All features within k-ring of an H3 cell',
    'H3',
    'SELECT DISTINCT f.id, f.external_id,
          ST_AsGeoJSON(f.geometry)::jsonb AS geometry, f.attributes
    FROM tessera.h3_cell_index h
    JOIN tessera.geo_features f ON f.id = h.feature_id AND f.ingested_at = h.feature_ingest
    WHERE h.resolution = :resolution
     AND h.h3_index = ANY(h3_grid_disk(:h3CellAddress::h3index, :k))',
    '{"type":"object","properties":{"h3CellAddress":{"type":"string"},
    "resolution":{"type":"integer"},
    "k":{"type":"integer","minimum":1}}}'::jsonb,
    false,
    ARRAY['h3','kring']),

    ('features.live_delta',
    'Streaming: features updated after a given timestamp',
    'LIVE',
    'SELECT f.id, f.external_id, f.source_id, f.source_table,
          ST_AsGeoJSON(f.geometry)::jsonb AS geometry,
          f.attributes, f.updated_at
    FROM tessera.geo_features f
    WHERE f.updated_at > :since
     AND (:sourceId IS NULL OR f.source_id = :sourceId::uuid)
    ORDER BY f.updated_at ASC',
    '{"type":"object","properties":{"since":{"type":"string","format":"date-time"},
    "sourceId":{"type":"string","nullable":true}}}'::jsonb,
    true,
    ARRAY['live','delta','streaming']),

    ('sources.sync_status',
    'Sync status and lag for all external sources',
    'STATIC',
    'SELECT es.name, es.source_type, es.sync_strategy, es.is_active,
          sc.table_name, sc.checkpoint_type, sc.checkpoint_value,
          sc.rows_processed, sc.last_synced_at,
          now() - sc.last_synced_at AS lag
    FROM tessera.external_sources es
    LEFT JOIN tessera.sync_checkpoints sc ON sc.source_id = es.id
    ORDER BY es.name, sc.table_name',
    '{}'::jsonb,
    false,
    ARRAY['ops','sync','monitoring']);