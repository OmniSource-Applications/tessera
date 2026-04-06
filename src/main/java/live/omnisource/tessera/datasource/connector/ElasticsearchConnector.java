package live.omnisource.tessera.datasource.connector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import live.omnisource.tessera.datasource.ExternalDataSourceFactory;
import live.omnisource.tessera.datasource.introspection.ElasticsearchIntrospector;
import live.omnisource.tessera.exceptions.DataStoreNotFoundException;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.model.dto.ColumnMetadata;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class ElasticsearchConnector implements DataSourceConnector {

  private static final Set<String> GEO_TYPES = Set.of("geo_point", "geo_shape");

  private final ExternalDataSourceFactory externalDataSourceFactory;
  private final SecureFileStore secureFileStore;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  public ElasticsearchConnector(
      ExternalDataSourceFactory externalDataSourceFactory,
      SecureFileStore secureFileStore,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.externalDataSourceFactory = externalDataSourceFactory;
    this.secureFileStore = secureFileStore;
    this.objectMapper = objectMapper;
  }

  @Override
  public SourceType sourceType() {
    return SourceType.ELASTICSEARCH;
  }

  @Override
  public ConnectionInfo testConnection(String sourceKey, ExternalSourceCredentials credentials) {
    try {
      log.info("Testing Elasticsearch connection with credentials: {}", credentials);
      var client = buildClient(credentials);
      var info = client.info();

      String version =
              "Elasticsearch " + info.version().number()
                      + " (cluster: " + info.clusterName() + ")";

      externalDataSourceFactory.storeCredentials(sourceKey, credentials);
      return ConnectionInfo.ok(version);
    } catch (Exception e) {
      log.error("Failed to test Elasticsearch connection", e);
      return ConnectionInfo.failed(e.getMessage());
    }
  }

  @Override
  public List<String> listSchemas(String secretRefKey) {
    return List.of("_all");
  }

  @Override
  public List<String> listTables(String secretRefKey, String schema) {
    try {
      var client = buildClientFromStore(secretRefKey);
      IndicesResponse response = client.cat().indices();

      return response.valueBody().stream()
              .map(idx -> idx.index())
              .filter(name -> name != null && !name.startsWith("."))
              .sorted()
              .toList();
    } catch (Exception e) {
      log.error("Failed to list Elasticsearch indices", e);
      throw new RuntimeException("Failed to list indices: " + e.getMessage(), e);
    }
  }

  @Override
  public SchemaMetadata introspectTable(String secretRefKey, String schema, String index) {
    try {
      var client = buildClientFromStore(secretRefKey);
      GetMappingResponse mapping = client.indices().getMapping(b -> b.index(index));
      var indexMapping = mapping.get(index);
      if (indexMapping == null) {
        throw new DataStoreNotFoundException("Index not found: " + index);
      }

      var properties = indexMapping.mappings().properties();
      var columns = new ArrayList<ColumnMetadata>();

      if (properties != null) {
        for (var entry : properties.entrySet()) {
          String fieldName = entry.getKey();
          Property prop = entry.getValue();
          String typeName = prop._kind().jsonValue();
          boolean isGeo = GEO_TYPES.contains(typeName);

          columns.add(new ColumnMetadata(fieldName, typeName, typeName, true, false, isGeo));
        }
      }

      long docCount = -1;
      try {
        var countResp = client.count(c -> c.index(index));
        docCount = countResp.count();
      } catch (Exception ignored) {
      }

      boolean hasGeo = columns.stream().anyMatch(ColumnMetadata::isGeometry);
      return new SchemaMetadata("_all", index, columns, docCount, hasGeo);

    } catch (DataStoreNotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to introspect index: " + index, e);
    }
  }

  @Override
  public Stream<RawRecord> streamTable(
      String secretRefKey, String schema, String index, StreamOptions opts) {
    try {
      var client = buildClientFromStore(secretRefKey);
      var searchResp = client.search(s -> s.index(index).size(opts.fetchSize()), Map.class);

      var hits = searchResp.hits().hits();
      var records =
          hits.stream()
              .map(
                  hit -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) hit.source();
                    if (source == null) {
                      source = Map.of();
                    }
                    var fields = new LinkedHashMap<>(source);
                    fields.put("_id", hit.id());
                    return new RawRecord("_all", index, fields);
                  })
              .toList();

      return records.stream();

    } catch (Exception e) {
      throw new RuntimeException("Failed to stream index " + index, e);
    }
  }

  @Override
  public IntrospectionResult introspect(String secretRefKey) {
    try {
      var client = buildClientFromStore(secretRefKey);
      return ElasticsearchIntrospector.introspect(client);
    } catch (Exception e) {
      throw new RuntimeException("Elasticsearch introspection failed: " + e.getMessage(), e);
    }
  }

  private ElasticsearchClient buildClient(ExternalSourceCredentials creds) {

    String host = normalizeHost(creds.hosts());

    HttpHost httpHost = HttpHost.create(host);

    RestClientBuilder builder = RestClient.builder(httpHost);

    // Optional authentication
    if (creds.username() != null && !creds.username().isBlank()) {

      final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
              AuthScope.ANY,
              new UsernamePasswordCredentials(
                      creds.username(),
                      creds.password()
              )
      );

      builder.setHttpClientConfigCallback(
              httpClientBuilder ->
                      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
      );
    }

    RestClient restClient = builder.build();

    ElasticsearchTransport transport =
            new RestClientTransport(restClient, new JacksonJsonpMapper());

    return new ElasticsearchClient(transport);
  }

  private ElasticsearchClient buildClientFromStore(String secretRefKey) {
    return buildClient(loadCredentials(secretRefKey));
  }

  private String normalizeHost(String host) {
    if (host.startsWith("http://") || host.startsWith("https://")) {
      return host;
    }
    return "http://" + host;
  }

  private ExternalSourceCredentials loadCredentials(String secretRefKey) {
    byte[] json = secureFileStore.get(secretRefKey);
    if (json == null) {
      throw new DataStoreNotFoundException("No credentials found for: " + secretRefKey);
    }
    try {
      return objectMapper.readValue(json, ExternalSourceCredentials.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read credentials for: " + secretRefKey, e);
    }
  }
}
