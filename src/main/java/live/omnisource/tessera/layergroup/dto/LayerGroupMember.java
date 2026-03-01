package live.omnisource.tessera.layergroup.dto;

/**
 * A member of a layer group — references a layer by its workspace/datastore/layer
 * path and caches the resolved source_id + source_table for efficient geo_features queries.
 *
 * @param workspace   workspace name
 * @param datastore   datastore name within the workspace
 * @param layer       layer name within the datastore
 * @param sourceId    cached external_sources.id (UUID as string), resolved at add-time
 * @param sourceTable cached "schema.table" from the layer record (e.g. "public.roads")
 */
public record LayerGroupMember(
        String workspace,
        String datastore,
        String layer,
        String sourceId,
        String sourceTable
) {

    /**
     * Natural key for deduplication — a member is unique by its layer path.
     */
    public String layerPath() {
        return workspace + "/" + datastore + "/" + layer;
    }
}
