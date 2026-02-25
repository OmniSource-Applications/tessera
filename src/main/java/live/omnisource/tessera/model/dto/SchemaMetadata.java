package live.omnisource.tessera.model.dto;

import java.util.List;

public record SchemaMetadata(
        String schema,
        String table,
        List<ColumnMetadata> columns,
        long rowEstimate,
        boolean hasGeometry
) {
    public List<ColumnMetadata> geometryColumns() {
        return columns.stream().filter(ColumnMetadata::isGeometry).toList();
    }

    public List<ColumnMetadata> primaryKeyColumns() {
        return columns.stream().filter(ColumnMetadata::isPrimaryKey).toList();
    }
}
