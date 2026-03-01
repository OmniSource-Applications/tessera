CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DROP TABLE IF EXISTS places CASCADE;
DROP TABLE IF EXISTS routes CASCADE;
DROP TABLE IF EXISTS areas CASCADE;

CREATE TABLE places (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text NOT NULL,
  category text NOT NULL,
  geom geometry(Point, 4326) NOT NULL
);
CREATE INDEX places_gix ON places USING GIST (geom);

CREATE TABLE routes (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text NOT NULL,
  geom geometry(LineString, 4326) NOT NULL
);
CREATE INDEX routes_gix ON routes USING GIST (geom);

CREATE TABLE areas (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text NOT NULL,
  geom geometry(Polygon, 4326) NOT NULL
);
CREATE INDEX areas_gix ON areas USING GIST (geom);

-- A few demo places around Colorado Springs (approx.)
INSERT INTO places (name, category, geom) VALUES
 ('Garden of the Gods', 'park', ST_SetSRID(ST_MakePoint(-104.8729, 38.8784), 4326)),
 ('Pikes Peak', 'mountain', ST_SetSRID(ST_MakePoint(-105.0423, 38.8409), 4326)),
 ('Downtown Colorado Springs', 'city', ST_SetSRID(ST_MakePoint(-104.8214, 38.8339), 4326)),
 ('USAF Academy', 'military', ST_SetSRID(ST_MakePoint(-104.8637, 39.0103), 4326));

-- A sample route (LineString)
INSERT INTO routes (name, geom) VALUES
 ('Demo Route', ST_SetSRID(ST_GeomFromText('LINESTRING(-104.8729 38.8784, -104.8214 38.8339)'), 4326));

-- A sample area (Polygon)
INSERT INTO areas (name, geom) VALUES
 ('Demo Area', ST_SetSRID(ST_GeomFromText('POLYGON((-104.86 38.86, -104.80 38.86, -104.80 38.82, -104.86 38.82, -104.86 38.86))'), 4326));
