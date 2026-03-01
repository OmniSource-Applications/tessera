#!/bin/sh
set -eu

ES="http://elasticsearch:9200"

echo "Waiting for Elasticsearch..."
# healthcheck already passed, but a small retry loop makes it more robust
for i in $(seq 1 20); do
  if curl -fsS "$ES" >/dev/null; then
    break
  fi
  sleep 1
done

echo "Creating indices..."
curl -fsS -X PUT "$ES/demo-places" -H 'Content-Type: application/json' -d '{
  "mappings": {
    "properties": {
      "name": { "type": "text" },
      "category": { "type": "keyword" },
      "location": { "type": "geo_point" }
    }
  }
}' >/dev/null || true

curl -fsS -X PUT "$ES/demo-events" -H 'Content-Type: application/json' -d '{
  "mappings": {
    "properties": {
      "device_id": { "type": "keyword" },
      "ts": { "type": "date" },
      "type": { "type": "keyword" },
      "payload": { "type": "object", "enabled": false }
    }
  }
}' >/dev/null || true

echo "Seeding docs..."
curl -fsS -X POST "$ES/demo-places/_bulk" -H 'Content-Type: application/x-ndjson' --data-binary @- >/dev/null <<'NDJSON'
{"index":{"_id":"p1"}}
{"name":"Garden of the Gods","category":"park","location":{"lat":38.8784,"lon":-104.8729}}
{"index":{"_id":"p2"}}
{"name":"Pikes Peak","category":"mountain","location":{"lat":38.8409,"lon":-105.0423}}
{"index":{"_id":"p3"}}
{"name":"Downtown Colorado Springs","category":"city","location":{"lat":38.8339,"lon":-104.8214}}
NDJSON

curl -fsS -X POST "$ES/demo-events/_bulk" -H 'Content-Type: application/x-ndjson' --data-binary @- >/dev/null <<'NDJSON'
{"index":{"_id":"e1"}}
{"device_id":"dev-001","ts":"2026-02-20T17:00:00Z","type":"BOOT","payload":{"msg":"device started"}}
{"index":{"_id":"e2"}}
{"device_id":"dev-001","ts":"2026-02-20T17:05:00Z","type":"POS","payload":{"lat":38.8339,"lon":-104.8214}}
{"index":{"_id":"e3"}}
{"device_id":"dev-002","ts":"2026-02-20T17:06:00Z","type":"POS","payload":{"lat":38.8784,"lon":-104.8729}}
NDJSON

echo "Done."
