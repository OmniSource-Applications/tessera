package live.omnisource.tessera.integration.catalog;

import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.catalog.repository.CatalogRepository;
import live.omnisource.tessera.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRepositoryIT extends IntegrationTestBase {

    @Autowired
    CatalogRepository repo;

    @Test
    void findByName_returnsSeededQuery() {
        var entry = repo.findByName("features.by_bbox");
        assertThat(entry).isPresent();
        assertThat(entry.get().getCategory()).isEqualTo("STATIC");
        assertThat(entry.get().getQuerySql()).contains("ST_Intersects");
    }

    @Test
    void findByCategoryOrderByNameAsc_returnsH3Queries() {
        var entries = repo.findByCategoryOrderByNameAsc("H3");
        assertThat(entries).isNotEmpty();
        assertThat(entries).allMatch(e -> e.getCategory().equals("H3"));
        // Verify ordering
        var names = entries.stream().map(CatalogEntry::getName).toList();
        assertThat(names).isSorted();
    }

    @Test
    void existsByName_trueForSeededEntry() {
        assertThat(repo.existsByName("features.by_bbox")).isTrue();
    }

    @Test
    void existsByName_falseForMissing() {
        assertThat(repo.existsByName("does.not.exist")).isFalse();
    }

    @Test
    void saveAndFind_customEntry() {
        var entry = new CatalogEntry();
        entry.setName("test.integration_" + System.nanoTime());
        entry.setCategory("CUSTOM");
        entry.setQuerySql("SELECT 1 AS test_col");
        entry.setTimeoutMs(5000);
        entry.setTags(List.of("integration-test"));
        entry.setParamSchema(Map.of("type", "object", "properties", Map.of()));

        var saved = repo.save(entry);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        var found = repo.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(entry.getName());
        assertThat(found.get().getTags()).containsExactly("integration-test");

        // Cleanup
        repo.deleteById(saved.getId());
    }
}