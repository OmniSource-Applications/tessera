package live.omnisource.tessera.datasource.introspection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.IntrospectionResult.SpatialTable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elasticsearch geo field discovery.
 *
 * <p>Scans all non-system indices for fields typed as {@code geo_point} or
 * {@code geo_shape}. Each matching field is reported as a "SpatialTable"
 * with the index as both schema and table.</p>
 */
@Slf4j
public final class ElasticsearchIntrospector {

    private ElasticsearchIntrospector() {}

    private static final Set<String> GEO_TYPES = Set.of("geo_point", "geo_shape");

    public static IntrospectionResult introspect(ElasticsearchClient client) {
        List<SpatialTable> tables = new ArrayList<>();

        try {
            IndicesResponse indices = client.cat().indices();
            List<String> indexNames = indices.valueBody().stream()
                    .map(IndicesRecord::index)
                    .filter(n -> n != null && !n.startsWith("."))
                    .sorted()
                    .toList();

            for (String index : indexNames) {
                try {
                    GetMappingResponse mapping = client.indices().getMapping(b -> b.index(index));
                    var indexMapping = mapping.get(index);
                    if (indexMapping == null) continue;

                    Map<String, Property> properties = indexMapping.mappings().properties();
                    long docCount = getDocCount(client, index);

                    for (var entry : properties.entrySet()) {
                        String fieldName = entry.getKey();
                        String typeName = entry.getValue()._kind().jsonValue();

                        if (GEO_TYPES.contains(typeName)) {
                            String geoType = typeName.equals("geo_point") ? "POINT" : "GEOMETRY";
                            tables.add(new SpatialTable(
                                    "_all", index, fieldName, geoType,
                                    4326, docCount, null
                            ));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to introspect index {}: {}", index, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Elasticsearch introspection failed: {}", e.getMessage(), e);
            throw new RuntimeException("Introspection failed: " + e.getMessage(), e);
        }

        log.info("Elasticsearch introspection: discovered {} geo fields across indices", tables.size());
        return new IntrospectionResult(tables);
    }

    private static long getDocCount(ElasticsearchClient client, String index) {
        try {
            return client.count(c -> c.index(index)).count();
        } catch (Exception e) {
            return -1;
        }
    }
}