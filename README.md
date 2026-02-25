<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/static/images/tessera dark.png" width="250">
  <source media="(prefers-color-scheme: light)" srcset="src/main/resources/static/images/terssera light.png" width="250">
  <img alt="tessera logo" src="src/main/resources/static/images/tessera dark.png" width="250">
</picture>

Tessera is an opensource geospatial platform that allows users to aggregate geospatial data from multiple data 
sources and perform spatial analysis and live-streaming of data to web map applications. 
Built on Uber's H3 spatial index system, tessera is designed for interoperability between
different data sources allowing users to fuse, modify, and visualize geospatial data in real-time.


## License

---

TODO: GPL

## Using

---

### Currently Supported Data Sources

| Source | Driver / Extension | Spatial Support | Notes |
|---|---|---|---|
| PostgreSQL | `postgresql` + PostGIS | Native (geometry, geography types) | Primary recommended backend |
| Cassandra | `cassandra-driver` | Manual (lat/lng columns) | Wide-column store suited for high-throughput stream ingestion |
| Elasticsearch | `elasticsearch-rest-client` | Native (`geo_point`, `geo_shape`) | Full-text search + spatial queries; ideal for combined text/geo workloads |
| Oracle DB | `ojdbc` + Oracle Spatial | Native (SDO_GEOMETRY) | Enterprise spatial with coordinate system support |
| MySQL | `mysql-connector` | Native (8.0+ spatial types) | Basic spatial indexing via R-tree; limited compared to PostGIS |



TODO: User Guide

## Building

---

TODO: Build Guide

## Contributing

---

TODO: Contributing Guide and policy

## Support

---

TODO: Community Support Page

## Bugs

---

TODO: Bug issues

