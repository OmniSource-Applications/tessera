package live.omnisource.tessera.model.dto;

import java.util.Map;

public record RawRecord(
        String schema,
        String table,
        Map<String, Object> fields
) {
    public Object get(String field) { return fields.get(field); }
    public boolean hasField(String field) { return fields.containsKey(field); }
}
