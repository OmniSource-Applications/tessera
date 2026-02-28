-- Runs as postgres superuser via docker-entrypoint-initdb.d
-- All CREATE EXTENSION calls must live here, NOT in Flyway migrations,
-- because extensions require superuser and Flyway runs as the app user.

\connect tessera

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS h3;
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman SCHEMA partman;

-- Grant app user access to partman schema
GRANT USAGE ON SCHEMA partman TO tessera;
GRANT SELECT ON ALL TABLES IN SCHEMA partman TO tessera;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO tessera;
GRANT ALL ON ALL TABLES IN SCHEMA partman TO tessera;

-- Allow app user to create schemas (needed for Flyway baseline)
GRANT CREATE ON DATABASE tessera TO tessera;