CREATE KEYSPACE IF NOT EXISTS demo
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS demo.devices_by_id (
  device_id text PRIMARY KEY,
  name text,
  lat double,
  lon double,
  updated_at timestamp
);

CREATE TABLE IF NOT EXISTS demo.events_by_device (
  device_id text,
  ts timestamp,
  type text,
  payload text,
  PRIMARY KEY (device_id, ts)
) WITH CLUSTERING ORDER BY (ts DESC);
