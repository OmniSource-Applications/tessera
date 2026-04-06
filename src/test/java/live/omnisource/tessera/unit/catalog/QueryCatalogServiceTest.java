package live.omnisource.tessera.unit.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.repository.CatalogRepository;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import live.omnisource.tessera.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class QueryCatalogServiceTest {

  @Mock CatalogRepository repo;

  QueryCatalogService service;

  @BeforeEach
  void setUp() {
    service = new QueryCatalogService(repo);
  }

  // ── Create ──────────────────────────────────────

  @Test
  void create_savesValidEntry() {
    var entry = TestDataFactory.catalogEntry("test.query");
    when(repo.existsByName("test.query")).thenReturn(false);
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.create(entry);

    assertThat(result.getName()).isEqualTo("test.query");
    verify(repo).save(entry);
  }

  @Test
  void create_rejectsDuplicateName() {
    var entry = TestDataFactory.catalogEntry("existing.query");
    when(repo.existsByName("existing.query")).thenReturn(true);

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class)
        .hasMessageContaining("already exists");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "INVALID NAME", "123start", "has space"})
  void create_rejectsInvalidNames(String badName) {
    var entry = TestDataFactory.catalogEntry("temp");
    entry.setName(badName);

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class);
  }

  @Test
  void create_rejectsNullName() {
    var entry = TestDataFactory.catalogEntry("temp");
    entry.setName(null);

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"STATIC", "H3", "LIVE", "CUSTOM"})
  void create_acceptsValidCategories(String category) {
    var entry = TestDataFactory.catalogEntry("test." + category.toLowerCase());
    entry.setCategory(category);
    when(repo.existsByName(any())).thenReturn(false);
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatNoException().isThrownBy(() -> service.create(entry));
  }

  @Test
  void create_rejectsInvalidCategory() {
    var entry = TestDataFactory.catalogEntry("test.badcat");
    entry.setCategory("BOGUS");

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class)
        .hasMessageContaining("Invalid category");
  }

  // ── SQL Safety ──────────────────────────────────

  @ParameterizedTest
  @ValueSource(
      strings = {"INSERT INTO x", "UPDATE x SET", "DELETE FROM x", "DROP TABLE x", "TRUNCATE x"})
  void create_blocksWriteOperations(String sql) {
    var entry = TestDataFactory.catalogEntry("test.blocked");
    entry.setQuerySql(sql);
    when(repo.existsByName(any())).thenReturn(false);

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class)
        .hasMessageContaining("read-only");
  }

  @Test
  void create_rejectsBlankSql() {
    var entry = TestDataFactory.catalogEntry("test.blanksql");
    entry.setQuerySql("   ");

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class);
  }

  // ── Timeout ─────────────────────────────────────

  @Test
  void create_rejectsTooLowTimeout() {
    var entry = TestDataFactory.catalogEntry("test.timeout");
    entry.setTimeoutMs(10);
    when(repo.existsByName(any())).thenReturn(false);

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class)
        .hasMessageContaining("Timeout");
  }

  @Test
  void create_rejectsTooHighTimeout() {
    var entry = TestDataFactory.catalogEntry("test.timeout");
    entry.setTimeoutMs(999999);
    when(repo.existsByName(any())).thenReturn(false);

    assertThatThrownBy(() -> service.create(entry))
        .isInstanceOf(DataStoreValidationException.class)
        .hasMessageContaining("Timeout");
  }

  // ── Read ────────────────────────────────────────

  @Test
  void getByName_throwsForMissing() {
    when(repo.findByName("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByName("nope"))
        .isInstanceOf(DataStoreValidationException.class);
  }

  // ── Delete ──────────────────────────────────────

  @Test
  void delete_throwsForMissingId() {
    var id = UUID.randomUUID();
    when(repo.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(DataStoreValidationException.class);
  }

  @Test
  void delete_deletesExisting() {
    var id = UUID.randomUUID();
    when(repo.existsById(id)).thenReturn(true);

    service.delete(id);
    verify(repo).deleteById(id);
  }
}
