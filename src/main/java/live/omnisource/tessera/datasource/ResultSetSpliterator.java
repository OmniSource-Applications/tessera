package live.omnisource.tessera.datasource;

import live.omnisource.tessera.model.dto.RawRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Spliterators;
import java.util.function.Consumer;

public class ResultSetSpliterator extends Spliterators.AbstractSpliterator<RawRecord> {

    private final ResultSet rs;
    private final String[]  columnNames;
    private final Connection conn;
    private final Statement stmt;
    private final String     schema;
    private final String     table;
    private boolean          closed = false;

    public ResultSetSpliterator(ResultSet rs, String[] columnNames,
                                Connection conn, Statement stmt,
                                String schema, String table) {
        super(Long.MAX_VALUE, ORDERED | NONNULL);
        this.rs          = rs;
        this.columnNames = columnNames;
        this.conn        = conn;
        this.stmt        = stmt;
        this.schema      = schema;
        this.table       = table;
    }

    @Override
    public boolean tryAdvance(Consumer<? super RawRecord> action) {
        if (closed) return false;
        try {
            if (!rs.next()) {
                closeQuietly();
                return false;
            }
            var fields = new LinkedHashMap<String, Object>(columnNames.length);
            for (int i = 0; i < columnNames.length; i++) {
                fields.put(columnNames[i], rs.getObject(i + 1));
            }
            action.accept(new RawRecord(schema, table, fields));
            return true;
        } catch (SQLException e) {
            closeQuietly();
            throw new RuntimeException("ResultSet read error on " + schema + "." + table, e);
        }
    }

    public void closeQuietly() {
        if (closed) return;
        closed = true;
        try { rs.close();   } catch (Exception ignored) {}
        try { stmt.close(); } catch (Exception ignored) {}
        try { conn.close(); } catch (Exception ignored) {}
    }
}
