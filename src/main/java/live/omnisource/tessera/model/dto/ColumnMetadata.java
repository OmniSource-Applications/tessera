package live.omnisource.tessera.model.dto;

public record ColumnMetadata(
        String name,
        String dataType,
        String nativeType,
        boolean nullable,
        boolean isPrimaryKey,
        boolean isGeometry
) {
}
