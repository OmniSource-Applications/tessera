SET search_path TO tessera, public;

-- ── H3 Index Table (multi-resolution) ────────────────────────────────────
-- Partitioned by LIST (resolution) so each resolution lives in its own
-- physical partition. PRIMARY KEY must include the partition key (resolution).
CREATE TABLE h3_cell_index (
                               id             BIGINT GENERATED ALWAYS AS IDENTITY,
                               feature_id     BIGINT NOT NULL,
                               feature_ingest TIMESTAMPTZ NOT NULL,
                               resolution     SMALLINT NOT NULL CHECK (resolution BETWEEN 0 AND 15),
                               h3_index       h3index NOT NULL,
                               h3_index_int   BIGINT NOT NULL,
                               center_lat     DOUBLE PRECISION,
                               center_lng     DOUBLE PRECISION,
                               is_compact     BOOLEAN NOT NULL DEFAULT false,
                               indexed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- resolution must be part of the PK because it is the partition key
                               PRIMARY KEY (id, resolution),
                               FOREIGN KEY (feature_id, feature_ingest)
                                   REFERENCES geo_features(id, ingested_at)
                                   ON DELETE CASCADE
) PARTITION BY LIST (resolution);

-- ── Resolution partitions ─────────────────────────────────────────────────
CREATE TABLE h3_cell_index_r3  PARTITION OF h3_cell_index FOR VALUES IN (3);
CREATE TABLE h3_cell_index_r5  PARTITION OF h3_cell_index FOR VALUES IN (5);
CREATE TABLE h3_cell_index_r7  PARTITION OF h3_cell_index FOR VALUES IN (7);
CREATE TABLE h3_cell_index_r9  PARTITION OF h3_cell_index FOR VALUES IN (9);
CREATE TABLE h3_cell_index_r11 PARTITION OF h3_cell_index FOR VALUES IN (11);

-- ── Indexes per partition ─────────────────────────────────────────────────
-- Defined on each child directly — parent-level indexes are not supported
-- for partitioned tables in PG 16 without pg_partman managing them.
CREATE INDEX idx_h3_r3_cell    ON h3_cell_index_r3  (h3_index);
CREATE INDEX idx_h3_r3_int     ON h3_cell_index_r3  (h3_index_int);
CREATE INDEX idx_h3_r3_feature ON h3_cell_index_r3  (feature_id);

CREATE INDEX idx_h3_r5_cell    ON h3_cell_index_r5  (h3_index);
CREATE INDEX idx_h3_r5_int     ON h3_cell_index_r5  (h3_index_int);
CREATE INDEX idx_h3_r5_feature ON h3_cell_index_r5  (feature_id);

CREATE INDEX idx_h3_r7_cell    ON h3_cell_index_r7  (h3_index);
CREATE INDEX idx_h3_r7_int     ON h3_cell_index_r7  (h3_index_int);
CREATE INDEX idx_h3_r7_feature ON h3_cell_index_r7  (feature_id);

CREATE INDEX idx_h3_r9_cell    ON h3_cell_index_r9  (h3_index);
CREATE INDEX idx_h3_r9_int     ON h3_cell_index_r9  (h3_index_int);
CREATE INDEX idx_h3_r9_feature ON h3_cell_index_r9  (feature_id);

CREATE INDEX idx_h3_r11_cell    ON h3_cell_index_r11  (h3_index);
CREATE INDEX idx_h3_r11_int     ON h3_cell_index_r11  (h3_index_int);
CREATE INDEX idx_h3_r11_feature ON h3_cell_index_r11  (feature_id);

-- ── H3 Density Materialized View (resolution 7) ───────────────────────────
CREATE MATERIALIZED VIEW mv_h3_density_r7 AS
SELECT
    h.h3_index,
    h.h3_index_int,
    count(*)                              AS feature_count,
    ST_Collect(f.geometry)                AS combined_geom,
    ST_ConvexHull(ST_Collect(f.geometry)) AS hull_geom,
    max(f.updated_at)                     AS last_updated
FROM h3_cell_index_r7 h
         JOIN geo_features f
              ON  f.id          = h.feature_id
                  AND f.ingested_at = h.feature_ingest
GROUP BY h.h3_index, h.h3_index_int
WITH NO DATA;

CREATE UNIQUE INDEX idx_mv_h3_density_r7 ON mv_h3_density_r7 (h3_index_int);

-- sync_checkpoints is defined in V1 — do not redefine here.