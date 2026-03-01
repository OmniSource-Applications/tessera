package live.omnisource.tessera.layergroup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted metadata for a layer group.
 * Written to {@code data_dir/etc/catalog/layergroups/{name}/group.json}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LayerGroupRecord(
        String name,
        String description,
        boolean active,
        List<LayerGroupMember> members,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Create a new empty group.
     */
    public static LayerGroupRecord create(String name, String description) {
        Instant now = Instant.now();
        return new LayerGroupRecord(name, description, true, List.of(), now, now);
    }

    public LayerGroupRecord withActive(boolean active) {
        return new LayerGroupRecord(name, description, active, members, createdAt, Instant.now());
    }

    public LayerGroupRecord withMembers(List<LayerGroupMember> members) {
        return new LayerGroupRecord(name, description, active, List.copyOf(members), createdAt, Instant.now());
    }

    public LayerGroupRecord withDescription(String description) {
        return new LayerGroupRecord(name, description, active, members, createdAt, Instant.now());
    }

    /**
     * Add a member, deduplicating by layer path.
     */
    public LayerGroupRecord withMemberAdded(LayerGroupMember member) {
        var updated = new ArrayList<>(members);
        // Remove existing entry for same layer path (idempotent add / refresh)
        updated.removeIf(m -> m.layerPath().equals(member.layerPath()));
        updated.add(member);
        return withMembers(updated);
    }

    /**
     * Remove a member by layer path.
     */
    public LayerGroupRecord withMemberRemoved(String workspace, String datastore, String layer) {
        String path = workspace + "/" + datastore + "/" + layer;
        var updated = new ArrayList<>(members);
        updated.removeIf(m -> m.layerPath().equals(path));
        return withMembers(updated);
    }

    /**
     * Collect the source_id UUIDs from all members as a string array.
     * Used to build the ANY(?::uuid[]) parameter for geo_features queries.
     */
    public String[] sourceIds() {
        return members.stream()
                .map(LayerGroupMember::sourceId)
                .distinct()
                .toArray(String[]::new);
    }
}
