package live.omnisource.tessera.unit.apikey;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.filestore.FileStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyFeatureToggleTest {

    @TempDir
    Path tempDir;

    FileStoreService fileStore;

    @BeforeEach
    void setUp() {
        fileStore = mock(FileStoreService.class);
        when(fileStore.resolve(any(String.class), any(String.class)))
                .thenReturn(tempDir.resolve("etc/auth/api-keys-enabled"));
    }

    @Test
    void defaultIsDisabled() {
        var toggle = new ApiKeyFeatureToggle(fileStore);
        assertThat(toggle.isEnabled()).isFalse();
    }

    @Test
    void enable_setsStateToTrue() {
        var toggle = new ApiKeyFeatureToggle(fileStore);
        toggle.enable();
        assertThat(toggle.isEnabled()).isTrue();
    }

    @Test
    void disable_setsStateToFalse() {
        var toggle = new ApiKeyFeatureToggle(fileStore);
        toggle.enable();
        toggle.disable();
        assertThat(toggle.isEnabled()).isFalse();
    }

    @Test
    void toggle_flipsState() {
        var toggle = new ApiKeyFeatureToggle(fileStore);
        assertThat(toggle.isEnabled()).isFalse();
        toggle.toggle();
        assertThat(toggle.isEnabled()).isTrue();
        toggle.toggle();
        assertThat(toggle.isEnabled()).isFalse();
    }

    @Test
    void readsPersistedState() throws IOException {
        Path file = tempDir.resolve("etc/auth/api-keys-enabled");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "true");

        var toggle = new ApiKeyFeatureToggle(fileStore);
        assertThat(toggle.isEnabled()).isTrue();
    }

    @Test
    void persistsStateOnEnable() throws IOException {
        var toggle = new ApiKeyFeatureToggle(fileStore);
        toggle.enable();

        Path file = tempDir.resolve("etc/auth/api-keys-enabled");
        assertThat(Files.readString(file).trim()).isEqualTo("true");
    }
}