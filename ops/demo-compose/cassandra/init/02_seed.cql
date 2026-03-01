INSERT INTO demo.devices_by_id (device_id, name, lat, lon, updated_at)
VALUES ('dev-001', 'tracker-alpha', 38.8339, -104.8214, toTimestamp(now()));

INSERT INTO demo.devices_by_id (device_id, name, lat, lon, updated_at)
VALUES ('dev-002', 'tracker-bravo', 38.8784, -104.8729, toTimestamp(now()));

INSERT INTO demo.events_by_device (device_id, ts, type, payload)
VALUES ('dev-001', toTimestamp(now()), 'BOOT', '{"msg":"device started"}');

INSERT INTO demo.events_by_device (device_id, ts, type, payload)
VALUES ('dev-001', toTimestamp(now()), 'POS', '{"lat":38.8339,"lon":-104.8214}');

INSERT INTO demo.events_by_device (device_id, ts, type, payload)
VALUES ('dev-002', toTimestamp(now()), 'POS', '{"lat":38.8784,"lon":-104.8729}');
