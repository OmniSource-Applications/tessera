package live.omnisource.tessera.unit.apikey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import live.omnisource.tessera.apikey.ApiKeyService;
import live.omnisource.tessera.apikey.entity.ApiKey;
import live.omnisource.tessera.apikey.repository.ApiKeyRepository;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

  @Mock ApiKeyRepository repo;

  ApiKeyService service;

  @BeforeEach
  void setUp() {
    service = new ApiKeyService(repo);
  }

  @Test
  void create_generatesKeyWithCorrectFormat() {
    when(repo.save(any(ApiKey.class)))
        .thenAnswer(
            inv -> {
              ApiKey k = inv.getArgument(0);
              k.setId(java.util.UUID.randomUUID());
              return k;
            });

    var result = service.create("test-key", "admin", null, 60, null);

    assertThat(result.rawKey()).startsWith("tsk_");
    assertThat(result.rawKey()).hasSize(4 + 32); // tsk_ + 32 hex chars
    assertThat(result.entity().getName()).isEqualTo("test-key");
    assertThat(result.entity().getOwner()).isEqualTo("admin");
    assertThat(result.entity().getKeyPrefix()).hasSize(8);
  }

  @Test
  void create_appliesDefaultScopes() {
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.create("k", "admin", null, 60, null);

    assertThat(result.entity().getScopes())
        .containsExactlyInAnyOrderElementsOf(ApiKeyService.DEFAULT_SCOPES);
  }

  @Test
  void create_rejectsInvalidScope() {
    assertThatThrownBy(() -> service.create("k", "admin", List.of("BOGUS"), 60, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid scope");
  }

  @Test
  void create_rejectsBlankName() {
    assertThatThrownBy(() -> service.create("", "admin", null, 60, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void create_setsExpiresAt() {
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Instant expires = Instant.now().plus(30, ChronoUnit.DAYS);
    var result = service.create("k", "admin", null, 60, expires);

    assertThat(result.entity().getExpiresAt()).isEqualTo(expires);
  }

  @Test
  void create_hashIsDeterministic() {
    // Two keys should have different hashes (different raw keys)
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var k1 = service.create("k1", "admin", null, 60, null);
    var k2 = service.create("k2", "admin", null, 60, null);

    assertThat(k1.entity().getKeyHash()).isNotEqualTo(k2.entity().getKeyHash());
  }

  @Test
  void validate_matchesCorrectKey() {
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              ApiKey k = inv.getArgument(0);
              k.setId(java.util.UUID.randomUUID());
              return k;
            });

    var created = service.create("k", "admin", null, 60, null);
    String rawKey = created.rawKey();
    ApiKey entity = created.entity();

    // Mock the lookup
    when(repo.findByKeyPrefixAndActiveTrue(entity.getKeyPrefix())).thenReturn(List.of(entity));

    var validated = service.validate(rawKey);
    assertThat(validated).isPresent();
    assertThat(validated.get().getName()).isEqualTo("k");
  }

  @Test
  void validate_rejectsInvalidKey() {
    var result = service.validate("tsk_0000000000000000000000000000000");
    assertThat(result).isEmpty();
  }

  @Test
  void validate_rejectsNullKey() {
    assertThat(service.validate(null)).isEmpty();
  }

  @Test
  void validate_rejectsWrongPrefix() {
    assertThat(service.validate("bearer_abc123")).isEmpty();
  }

  @Test
  void revoke_setsRevokedAtAndInactive() {
    var key = new ApiKey();
    key.setId(java.util.UUID.randomUUID());
    key.setActive(true);
    when(repo.findById(key.getId())).thenReturn(Optional.of(key));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.revoke(key.getId());

    assertThat(key.isActive()).isFalse();
    assertThat(key.getRevokedAt()).isNotNull();
  }

  @Test
  void revoke_throwsForMissingKey() {
    var id = java.util.UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revoke(id)).isInstanceOf(IllegalArgumentException.class);
  }
}
