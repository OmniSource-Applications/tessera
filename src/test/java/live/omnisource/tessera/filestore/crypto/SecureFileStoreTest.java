package live.omnisource.tessera.filestore.crypto;

import live.omnisource.tessera.exceptions.CryptoException;
import live.omnisource.tessera.filestore.FileStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.*;

class SecureFileStoreTest {

    @TempDir
    Path tmp;

    @Test
    void putGetDelete_roundTrip_withStableMasterKey() {
        // SecureFileStore uses FileStoreService's static baseDir (./data_dir).
        // Run inside a temp working directory so tests don't pollute the repo.
        var prev = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        try {
            var fs = new FileStoreService();
            fs.init();

            byte[] masterKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
            var store = new SecureFileStore(fs.secretsDir(), new SecureRandom(), masterKey, fs);

            String secretKey = "db/default/abc/passwd";
            String value = "tessera";
            store.put(secretKey, value.getBytes(StandardCharsets.UTF_8));

            assertThat(new String(store.get(secretKey), StandardCharsets.UTF_8)).isEqualTo(value);

            store.delete(secretKey);
            assertThat(store.get(secretKey)).isNull();

        } finally {
            System.setProperty("user.dir", prev);
        }
    }

    @Test
    void get_failsIntegrityCheck_ifMasterKeyIsWrong() {
        var prev = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        try {
            var fs = new FileStoreService();
            fs.init();

            var storeA = new SecureFileStore(fs.secretsDir(), new SecureRandom(),
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8), fs);
            var storeB = new SecureFileStore(fs.secretsDir(), new SecureRandom(),
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes(StandardCharsets.UTF_8), fs);

            String secretKey = "db/default/xyz/passwd";
            storeA.put(secretKey, "secret".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> storeB.get(secretKey))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("integrity check failed");

        } finally {
            System.setProperty("user.dir", prev);
        }
    }
}
