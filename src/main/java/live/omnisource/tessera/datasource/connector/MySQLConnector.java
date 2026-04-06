package live.omnisource.tessera.datasource.connector;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import live.omnisource.tessera.datasource.ExternalDataSourceFactory;
import live.omnisource.tessera.datasource.ResultSetSpliterator;
import live.omnisource.tessera.datasource.introspection.MysqlIntrospector;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.model.dto.ColumnMetadata;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import live.omnisource.tessera.util.SqlIdentifiers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class MySQLConnector implements DataSourceConnector {

  private static final Set<String> GEO_TYPES =
      Set.of(
          "geometry",
          "point",
          "linestring",
          "polygon",
          "multipoint",
          "multilinestring",
          "multipolygon",
          "geometrycollection");

  private final ExternalDataSourceFactory externalDataSourceFactory;

  public MySQLConnector(ExternalDataSourceFactory externalDataSourceFactory) {
    this.externalDataSourceFactory = externalDataSourceFactory;
  }

  private DataSource dataSource(String secretKeyRef) {
    return externalDataSourceFactory.forSecretRef(secretKeyRef);
  }

  @Override
  public SourceType sourceType() {
    return SourceType.MYSQL;
  }

  @Override
  public ConnectionInfo testConnection(String sourceKey, ExternalSourceCredentials credentials) {
    try (var c =
            DriverManager.getConnection(
                credentials.url(), credentials.username(), credentials.password());
        var st = c.createStatement();
        var rs = st.executeQuery("SELECT version()")) {
      rs.next();
      String version = "MySQL " + rs.getString(1);

      externalDataSourceFactory.storeCredentials(sourceKey, credentials);
      return ConnectionInfo.ok(version);
    } catch (SQLException e) {
      log.error("Failed to test MySQL connection: {}", e.getMessage());
      return ConnectionInfo.failed(e.getMessage());
    }
  }

  @Override
  public List<String> listSchemas(String secretRefKey) {
    var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
    return tmpl.queryForList(
        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
            + "WHERE SCHEMA_NAME NOT IN ('mysql','information_schema','performance_schema','sys') ORDER BY 1",
        Map.of(),
        String.class);
  }

  @Override
  public List<String> listTables(String secretRefKey, String schema) {
    var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
    return tmpl.queryForList(
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = :s ORDER BY 1",
        Map.of("s", schema),
        String.class);
  }

  @Override
  public SchemaMetadata introspectTable(String secretRefKey, String schema, String table) {
    var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
    var sql =
        """
            SELECT c.COLUMN_NAME, c.DATA_TYPE, c.COLUMN_TYPE, c.IS_NULLABLE,
                   c.COLUMN_KEY
            FROM information_schema.COLUMNS c
            WHERE c.TABLE_SCHEMA = :s AND c.TABLE_NAME = :t
            ORDER BY c.ORDINAL_POSITION
            """;

    var params = Map.of("s", schema, "t", table);
    var columns =
        tmpl.query(
            sql,
            params,
            (rs, i) ->
                new ColumnMetadata(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("DATA_TYPE"),
                    rs.getString("COLUMN_TYPE"),
                    "YES".equals(rs.getString("IS_NULLABLE")),
                    "PRI".equals(rs.getString("COLUMN_KEY")),
                    isGeoType(rs.getString("DATA_TYPE"))));

    var rowCount =
        tmpl.queryForObject(
            "SELECT TABLE_ROWS FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = :s AND TABLE_NAME = :t",
            params,
            Long.class);

    boolean hasGeo = columns.stream().anyMatch(ColumnMetadata::isGeometry);
    return new SchemaMetadata(schema, table, columns, rowCount != null ? rowCount : -1L, hasGeo);
  }

  @Override
  public Stream<RawRecord> streamTable(
      String secretRefKey, String schema, String table, StreamOptions opts) {
    try {
      var conn = dataSource(secretRefKey).getConnection();
      conn.setAutoCommit(false);
      var sql = buildSql(schema, table, opts);
      var st = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      st.setFetchSize(Integer.MIN_VALUE); // MySQL streaming mode
      if (opts.checkpointValue() != null) st.setObject(1, opts.checkpointValue());
      var rs = st.executeQuery();
      var meta = rs.getMetaData();
      int n = meta.getColumnCount();
      var names = new String[n];
      for (int i = 1; i <= n; i++) names[i - 1] = meta.getColumnName(i);

      var spliterator = new ResultSetSpliterator(rs, names, conn, st, schema, table);
      return StreamSupport.stream(spliterator, false).onClose(spliterator::closeQuietly);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to stream " + schema + "." + table, e);
    }
  }

  @Override
  public IntrospectionResult introspect(String secretRefKey) {
    return MysqlIntrospector.introspect(dataSource(secretRefKey));
  }

  private String buildSql(String schema, String table, StreamOptions opts) {
    String safeSchema = SqlIdentifiers.quoteBacktick(schema, "schema");
    String safeTable = SqlIdentifiers.quoteBacktick(table, "table");
    var sb = new StringBuilder("SELECT * FROM ").append(safeSchema).append(".").append(safeTable);
    if (opts.checkpointValue() != null && opts.orderByColumn() != null) {
      String safeCol = SqlIdentifiers.quoteBacktick(opts.orderByColumn(), "orderByColumn");
      sb.append(" WHERE ").append(safeCol).append(" > ?");
    }
    if (opts.orderByColumn() != null) {
      String safeCol = SqlIdentifiers.quoteBacktick(opts.orderByColumn(), "orderByColumn");
      sb.append(" ORDER BY ").append(safeCol);
    }
    if (opts.maxRows() > 0) sb.append(" LIMIT ").append(opts.maxRows());
    return sb.toString();
  }

  private boolean isGeoType(String dataType) {
    return dataType != null && GEO_TYPES.contains(dataType.toLowerCase());
  }
}
