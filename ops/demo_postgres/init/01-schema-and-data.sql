-- ============================================================
-- Tessera Demo Database
-- Sample spatial data around Colorado Springs, CO
-- ============================================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- ============================================================
-- Points of Interest
-- ============================================================
CREATE TABLE points_of_interest (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    category    VARCHAR(100) NOT NULL,
    description TEXT,
    geom        GEOMETRY(Point, 4326) NOT NULL
);

CREATE INDEX idx_poi_geom ON points_of_interest USING GIST (geom);

INSERT INTO points_of_interest (name, category, description, geom) VALUES
-- Landmarks
('Garden of the Gods',        'landmark',    'Iconic red rock formations and park',           ST_SetSRID(ST_MakePoint(-104.8697, 38.8786), 4326)),
('Pikes Peak Summit',         'landmark',    '14,115 ft summit - America''s Mountain',        ST_SetSRID(ST_MakePoint(-105.0423, 38.8409), 4326)),
('Cheyenne Mountain',         'landmark',    'Home of NORAD and Cheyenne Mountain Zoo',       ST_SetSRID(ST_MakePoint(-104.8264, 38.7471), 4326)),
('Seven Falls',               'landmark',    'Series of seven cascading waterfalls',           ST_SetSRID(ST_MakePoint(-104.8810, 38.7733), 4326)),
('Manitou Incline',           'landmark',    '2,744 steps gaining 2,000ft elevation',         ST_SetSRID(ST_MakePoint(-104.9458, 38.8586), 4326)),

-- Restaurants
('Shuga''s',                  'restaurant',  'Eclectic cocktails and brunch downtown',         ST_SetSRID(ST_MakePoint(-104.8253, 38.8339), 4326)),
('The Rabbit Hole',           'restaurant',  'Creative American cuisine',                      ST_SetSRID(ST_MakePoint(-104.8262, 38.8319), 4326)),
('Pizzeria Rustica',          'restaurant',  'Wood-fired Neapolitan pizza',                    ST_SetSRID(ST_MakePoint(-104.8611, 38.8468), 4326)),
('The Famous Steak House',    'restaurant',  'Classic steakhouse since 1953',                  ST_SetSRID(ST_MakePoint(-104.8213, 38.8362), 4326)),
('Bird Tree Cafe',            'restaurant',  'Farm-to-table breakfast and lunch',              ST_SetSRID(ST_MakePoint(-104.8569, 38.8467), 4326)),

-- Parks
('North Cheyenne Canyon Park','park',        'Scenic canyon with hiking trails',               ST_SetSRID(ST_MakePoint(-104.8700, 38.7700), 4326)),
('Palmer Park',               'park',        'Mesa trails and red rock formations',            ST_SetSRID(ST_MakePoint(-104.7900, 38.8400), 4326)),
('Memorial Park',             'park',        'Largest urban park with Prospect Lake',          ST_SetSRID(ST_MakePoint(-104.7986, 38.8360), 4326)),
('Bear Creek Regional Park',  'park',        'Nature center and disc golf course',             ST_SetSRID(ST_MakePoint(-104.8550, 38.7950), 4326)),
('Red Rock Canyon Open Space','park',        'Trails through dramatic red rock geology',       ST_SetSRID(ST_MakePoint(-104.8780, 38.8520), 4326));

-- ============================================================
-- Trail Lines
-- ============================================================
CREATE TABLE trails (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    difficulty  VARCHAR(50) NOT NULL,
    length_mi   NUMERIC(5,2),
    geom        GEOMETRY(LineString, 4326) NOT NULL
);

CREATE INDEX idx_trails_geom ON trails USING GIST (geom);

INSERT INTO trails (name, difficulty, length_mi, geom) VALUES
('Perkins Central Garden Trail', 'easy', 1.5,
 ST_SetSRID(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.8720, 38.8750),
   ST_MakePoint(-104.8705, 38.8770),
   ST_MakePoint(-104.8680, 38.8790),
   ST_MakePoint(-104.8660, 38.8810)
 ]), 4326)),

('Intemann Trail', 'moderate', 4.2,
 ST_SetSRID(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.9100, 38.8500),
   ST_MakePoint(-104.9000, 38.8480),
   ST_MakePoint(-104.8900, 38.8450),
   ST_MakePoint(-104.8800, 38.8420),
   ST_MakePoint(-104.8700, 38.8400)
 ]), 4326)),

('Section 16 Loop', 'moderate', 5.8,
 ST_SetSRID(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.8780, 38.8520),
   ST_MakePoint(-104.8820, 38.8480),
   ST_MakePoint(-104.8860, 38.8440),
   ST_MakePoint(-104.8830, 38.8400),
   ST_MakePoint(-104.8790, 38.8440),
   ST_MakePoint(-104.8780, 38.8520)
 ]), 4326)),

('Barr Trail', 'hard', 12.6,
 ST_SetSRID(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.9420, 38.8560),
   ST_MakePoint(-104.9500, 38.8540),
   ST_MakePoint(-104.9700, 38.8500),
   ST_MakePoint(-104.9900, 38.8470),
   ST_MakePoint(-105.0100, 38.8440),
   ST_MakePoint(-105.0423, 38.8409)
 ]), 4326));

-- ============================================================
-- Neighborhood Polygons
-- ============================================================
CREATE TABLE neighborhoods (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    pop_est INTEGER,
    geom    GEOMETRY(Polygon, 4326) NOT NULL
);

CREATE INDEX idx_neighborhoods_geom ON neighborhoods USING GIST (geom);

INSERT INTO neighborhoods (name, pop_est, geom) VALUES
('Downtown', 5200,
 ST_SetSRID(ST_MakePolygon(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.8350, 38.8400),
   ST_MakePoint(-104.8150, 38.8400),
   ST_MakePoint(-104.8150, 38.8280),
   ST_MakePoint(-104.8350, 38.8280),
   ST_MakePoint(-104.8350, 38.8400)
 ])), 4326)),

('Old Colorado City', 8100,
 ST_SetSRID(ST_MakePolygon(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.8700, 38.8530),
   ST_MakePoint(-104.8500, 38.8530),
   ST_MakePoint(-104.8500, 38.8430),
   ST_MakePoint(-104.8700, 38.8430),
   ST_MakePoint(-104.8700, 38.8530)
 ])), 4326)),

('Manitou Springs', 5300,
 ST_SetSRID(ST_MakePolygon(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.9250, 38.8650),
   ST_MakePoint(-104.9050, 38.8650),
   ST_MakePoint(-104.9050, 38.8500),
   ST_MakePoint(-104.9250, 38.8500),
   ST_MakePoint(-104.9250, 38.8650)
 ])), 4326)),

('Broadmoor', 12000,
 ST_SetSRID(ST_MakePolygon(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.8600, 38.7900),
   ST_MakePoint(-104.8300, 38.7900),
   ST_MakePoint(-104.8300, 38.7700),
   ST_MakePoint(-104.8600, 38.7700),
   ST_MakePoint(-104.8600, 38.7900)
 ])), 4326)),

('Northgate', 15000,
 ST_SetSRID(ST_MakePolygon(ST_MakeLine(ARRAY[
   ST_MakePoint(-104.8200, 38.9200),
   ST_MakePoint(-104.7900, 38.9200),
   ST_MakePoint(-104.7900, 38.8950),
   ST_MakePoint(-104.8200, 38.8950),
   ST_MakePoint(-104.8200, 38.9200)
 ])), 4326));

-- ============================================================
-- Sensor / IoT points (for streaming demo later)
-- ============================================================
CREATE TABLE sensors (
    id          SERIAL PRIMARY KEY,
    sensor_id   VARCHAR(50) UNIQUE NOT NULL,
    type        VARCHAR(50) NOT NULL,
    status      VARCHAR(20) DEFAULT 'active',
    geom        GEOMETRY(Point, 4326) NOT NULL,
    installed   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_sensors_geom ON sensors USING GIST (geom);

INSERT INTO sensors (sensor_id, type, geom) VALUES
('WX-001', 'weather',       ST_SetSRID(ST_MakePoint(-104.8214, 38.8339), 4326)),
('WX-002', 'weather',       ST_SetSRID(ST_MakePoint(-104.8697, 38.8786), 4326)),
('WX-003', 'weather',       ST_SetSRID(ST_MakePoint(-104.7600, 38.9000), 4326)),
('AQ-001', 'air_quality',   ST_SetSRID(ST_MakePoint(-104.8253, 38.8340), 4326)),
('AQ-002', 'air_quality',   ST_SetSRID(ST_MakePoint(-104.8900, 38.8500), 4326)),
('TF-001', 'traffic',       ST_SetSRID(ST_MakePoint(-104.8200, 38.8670), 4326)),
('TF-002', 'traffic',       ST_SetSRID(ST_MakePoint(-104.8130, 38.8400), 4326)),
('TF-003', 'traffic',       ST_SetSRID(ST_MakePoint(-104.8450, 38.8100), 4326)),
('WL-001', 'water_level',   ST_SetSRID(ST_MakePoint(-104.7986, 38.8360), 4326)),
('WL-002', 'water_level',   ST_SetSRID(ST_MakePoint(-104.8550, 38.7950), 4326));

-- ============================================================
-- Sensor readings (time-series sample)
-- ============================================================
CREATE TABLE sensor_readings (
    id          BIGSERIAL PRIMARY KEY,
    sensor_id   VARCHAR(50) NOT NULL REFERENCES sensors(sensor_id),
    reading     JSONB NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_readings_sensor ON sensor_readings (sensor_id, recorded_at DESC);

-- Seed a few readings
INSERT INTO sensor_readings (sensor_id, reading, recorded_at) VALUES
('WX-001', '{"temp_f": 62, "humidity": 34, "wind_mph": 8}',    NOW() - INTERVAL '2 hours'),
('WX-001', '{"temp_f": 65, "humidity": 30, "wind_mph": 12}',   NOW() - INTERVAL '1 hour'),
('WX-001', '{"temp_f": 68, "humidity": 28, "wind_mph": 10}',   NOW()),
('AQ-001', '{"pm25": 8.2, "pm10": 14.5, "aqi": 42}',          NOW() - INTERVAL '1 hour'),
('AQ-001', '{"pm25": 9.1, "pm10": 15.8, "aqi": 45}',          NOW()),
('TF-001', '{"vehicles_per_min": 42, "avg_speed_mph": 35}',    NOW() - INTERVAL '30 minutes'),
('TF-001', '{"vehicles_per_min": 58, "avg_speed_mph": 28}',    NOW()),
('WL-001', '{"level_ft": 4.2, "flow_cfs": 120}',              NOW() - INTERVAL '1 hour'),
('WL-001', '{"level_ft": 4.3, "flow_cfs": 125}',              NOW());

-- ============================================================
-- Verify
-- ============================================================
DO $$
BEGIN
  RAISE NOTICE '✓ points_of_interest: % rows', (SELECT count(*) FROM points_of_interest);
  RAISE NOTICE '✓ trails: % rows',             (SELECT count(*) FROM trails);
  RAISE NOTICE '✓ neighborhoods: % rows',      (SELECT count(*) FROM neighborhoods);
  RAISE NOTICE '✓ sensors: % rows',            (SELECT count(*) FROM sensors);
  RAISE NOTICE '✓ sensor_readings: % rows',    (SELECT count(*) FROM sensor_readings);
END $$;
