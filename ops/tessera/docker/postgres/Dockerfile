FROM postgis/postgis:16-3.4

# That's it — one line for h3, one for partman.
# Both are in the PGDG apt repo which is already configured in this image.
RUN apt-get update -qq && apt-get install -y --no-install-recommends \
    postgresql-16-h3 \
    postgresql-16-partman \
    && rm -rf /var/lib/apt/lists/*

COPY ops/tessera/docker/postgres/init/00_extensions.sql /docker-entrypoint-initdb.d/00_extensions.sql