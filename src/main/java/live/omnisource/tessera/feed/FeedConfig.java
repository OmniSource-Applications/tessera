package live.omnisource.tessera.feed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Feed configuration — persisted as {@code data_dir/etc/feeds/{name}/feed.json}.
 *
 * <p>Defines how Tessera connects to an external live data source, what protocol
 * to use, how to parse incoming messages, and how to map fields to geo_features.</p>
 *
 * <p>All numeric/boolean fields use boxed types so Jackson can deserialize
 * partial JSON (e.g. a WebSocket config that omits pollIntervalMs).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedConfig(
        String name,
        FeedType type,
        Boolean enabled,
        ConnectionConfig connection,
        SchemaMapping schema,
        IngestConfig ingest,
        Instant createdAt,
        Instant updatedAt
) {
    /** Null-safe enabled check — defaults to false. */
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    public enum FeedType {
        REST_POLL,    // Polls a REST endpoint on interval
        TCP,          // Raw TCP socket (line-delimited or length-prefixed)
        WEBSOCKET,    // WebSocket client
        KAFKA,        // Kafka consumer
        AMQP          // AMQP 0-9-1 (RabbitMQ) or MQTT bridge
    }

    /**
     * Protocol-specific connection settings.
     * Only the fields relevant to the feed type need to be populated.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConnectionConfig(
            // ── REST_POLL ───────────────────────────
            String url,                // Full URL to poll
            String method,             // GET or POST (default GET)
            Map<String, String> headers, // Custom headers (e.g. Authorization)
            String body,               // Request body for POST
            Integer pollIntervalMs,    // Poll frequency (default 5000)

            // ── TCP ────────────────────────────────
            String host,
            Integer port,
            Boolean tls,
            String delimiter,          // Line delimiter (default \n)
            String framing,            // LINE_DELIMITED | LENGTH_PREFIX | RAW

            // ── WEBSOCKET ──────────────────────────
            String wsUrl,              // ws:// or wss:// URL
            String wsSubscribeMessage, // JSON message to send on connect

            // ── KAFKA ──────────────────────────────
            String bootstrapServers,
            String topic,
            String groupId,
            String keyDeserializer,
            String valueDeserializer,
            Map<String, String> kafkaProperties,

            // ── AMQP ──────────────────────────────
            String amqpUri,            // amqp://user:pass@host:5672/vhost
            String exchange,
            String queue,
            String routingKey,
            Boolean durableQueue
    ) {
        /** Resolved poll interval — defaults to 5000ms. */
        public int resolvedPollIntervalMs() {
            return (pollIntervalMs != null && pollIntervalMs > 0) ? pollIntervalMs : 5000;
        }
        /** Resolved port — defaults to 0 (unset). */
        public int resolvedPort() {
            return (port != null && port > 0) ? port : 0;
        }
        /** Resolved method — defaults to GET. */
        public String resolvedMethod() {
            return (method != null && !method.isBlank()) ? method : "GET";
        }
        /** Resolved TLS flag — defaults to false. */
        public boolean isTls() {
            return tls != null && tls;
        }
        /** Resolved delimiter — defaults to newline. */
        public String resolvedDelimiter() {
            return (delimiter != null) ? delimiter : "\n";
        }
        /** Resolved framing — defaults to LINE_DELIMITED. */
        public String resolvedFraming() {
            return (framing != null) ? framing : "LINE_DELIMITED";
        }
        /** Resolved durable queue flag — defaults to false. */
        public boolean isDurableQueue() {
            return durableQueue != null && durableQueue;
        }
    }

    /**
     * Defines how to parse incoming messages and extract geospatial features.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SchemaMapping(
            DataFormat format,         // How to parse the raw bytes
            GeometryMapping geometry,  // How to extract geometry
            String idField,            // Field for external_id (null = auto-generate)
            String timestampField,     // Field for event timestamp (null = now())
            List<String> attributeFields, // Fields to include in attributes JSONB (null = all)
            List<String> excludeFields,   // Fields to exclude from attributes
            String rootPath            // JSON path to array of records (e.g. "features" or "data.items")
    ) {}

    public enum DataFormat {
        JSON,              // Single JSON object per message
        JSON_LINES,        // Newline-delimited JSON (one object per line)
        JSON_ARRAY,        // JSON array of objects
        CSV,               // CSV with header row
        GEOJSON,           // GeoJSON FeatureCollection or Feature
        PROTOBUF,          // Protocol Buffers (requires schema registry)
        AVRO,              // Avro (requires schema registry)
        RAW_TEXT            // Raw text (stored as single attribute)
    }

    /**
     * How to extract geometry from each record.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeometryMapping(
            GeometryType type,
            String latField,           // For LAT_LNG
            String lngField,           // For LAT_LNG
            String geometryField,      // For WKT, WKB, GEOJSON
            Integer srid               // Default 4326
    ) {
        /** Resolved SRID — defaults to 4326. */
        public int resolvedSrid() {
            return (srid != null && srid > 0) ? srid : 4326;
        }
    }

    public enum GeometryType {
        LAT_LNG,      // Separate lat/lng fields → Point
        WKT,           // Well-Known Text string
        WKB,           // Well-Known Binary (hex)
        GEOJSON,       // GeoJSON geometry object
        H3,            // H3 cell index → centroid point
        MGRS,          // Military Grid Reference System → point
        NONE           // No geometry (attribute-only records)
    }

    /**
     * Controls ingestion behavior — batching, dedup, target workspace.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IngestConfig(
            String targetWorkspace,    // Workspace to associate features with
            String targetDatastore,    // Datastore name (created if needed)
            String sourceTable,        // source_table value in geo_features
            Integer batchSize,         // Batch write size (default 100)
            Integer flushIntervalMs,   // Max time to hold batch before writing (default 2000)
            Boolean deduplicateByHash, // SHA-256 hash dedup
            Boolean h3Index,           // Index with H3 on ingest
            int[] h3Resolutions        // H3 resolutions (default [7, 9])
    ) {
        /** Resolved batch size — defaults to 100. */
        public int resolvedBatchSize() {
            return (batchSize != null && batchSize > 0) ? batchSize : 100;
        }
        /** Resolved flush interval — defaults to 2000ms. */
        public int resolvedFlushIntervalMs() {
            return (flushIntervalMs != null && flushIntervalMs > 0) ? flushIntervalMs : 2000;
        }
        /** Resolved dedup flag — defaults to false. */
        public boolean isDeduplicateByHash() {
            return deduplicateByHash != null && deduplicateByHash;
        }
        /** Resolved H3 index flag — defaults to false. */
        public boolean isH3Index() {
            return h3Index != null && h3Index;
        }
        /** Resolved H3 resolutions — defaults to [7, 9]. */
        public int[] resolvedH3Resolutions() {
            return (h3Resolutions != null) ? h3Resolutions : new int[]{7, 9};
        }
    }
}