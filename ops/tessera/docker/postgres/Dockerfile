FROM postgis/postgis:16-3.4

RUN apt-get update -qq && apt-get install -y --no-install-recommends \
    postgresql-16-h3 \
    postgresql-16-partman \
    && rm -rf /var/lib/apt/lists/*

COPY init/00_extensions.sql /docker-entrypoint-initdb.d/00_extensions.sql