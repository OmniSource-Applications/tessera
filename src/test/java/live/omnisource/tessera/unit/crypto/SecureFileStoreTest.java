package live.omnisource.tessera.unit.crypto;

import live.omnisource.tessera.exceptions.CryptoException;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.*;

class SecureFileStoreTest {

    @TempDir
    Path tempDir;

    private SecureFileStore store;

    @BeforeEach
    void setUp() {
        byte[] masterKey = new byte[32];
        new SecureRandom().nextBytes(masterKey);
        store = new SecureFileStore(tempDir, new SecureRandom(), masterKey, null);
    }

    @Test
    void putAndGet_roundTripsData() {
        byte[] plaintext = "secret credentials".getBytes(StandardCharsets.UTF_8);
        store.put("my-key", plaintext);

        byte[] retrieved = store.get("my-key");
        assertThat(retrieved).isEqualTo(plaintext);
    }

    @Test
    void putAndGet_handlesLargeData() {
        byte[] large = new byte[64 * 1024]; // 64KB
        new SecureRandom().nextBytes(large);
        store.put("large-key", large);

        assertThat(store.get("large-key")).isEqualTo(large);
    }

    @Test
    void get_throwsForMissingKey() {
        assertThatThrownBy(() -> store.get("nonexistent"))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void put_throwsForNullPlaintext() {
        assertThatThrownBy(() -> store.put("key", null))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void put_throwsForBlankKey() {
        assertThatThrownBy(() -> store.put("", "data".getBytes()))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void exists_returnsTrueAfterPut() {
        store.put("check-key", "data".getBytes());
        assertThat(store.exists("check-key")).isTrue();
    }

    @Test
    void exists_returnsFalseForMissingKey() {
        assertThat(store.exists("nope")).isFalse();
    }

    @Test
    void delete_removesEntry() {
        store.put("del-key", "data".getBytes());
        assertThat(store.exists("del-key")).isTrue();

        store.delete("del-key");
        assertThat(store.exists("del-key")).isFalse();
    }

    @Test
    void differentKeys_produceDifferentCiphertexts() {
        byte[] plaintext = "same data".getBytes();
        store.put("key-a", plaintext);
        store.put("key-b", plaintext);

        // Files should differ (different DEKs, nonces)
        Path fileA = store.fileForKey("key-a");
        Path fileB = store.fileForKey("key-b");
        assertThat(fileA).isNotEqualTo(fileB);
    }

    @Test
    void wrongMasterKey_failsDecryption() {
        store.put("protected", "secret".getBytes());

        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);
        SecureFileStore wrongStore = new SecureFileStore(tempDir, new SecureRandom(), wrongKey, null);

        assertThatThrownBy(() -> wrongStore.get("protected"))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("integrity check failed");
    }
}