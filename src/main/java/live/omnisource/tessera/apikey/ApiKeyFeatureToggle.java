package live.omnisource.tessera.apikey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import lombok.extern.slf4j.Slf4j;

/**
 * Runtime feature toggle for API key authentication.
 *
 * <p>State is persisted as a single file at {@code data_dir/etc/auth/api-keys-enabled}. If the file
 * exists and contains "true", the feature is on. Otherwise it's off. Default is
 * <strong>off</strong>.
 */
@Slf4j
@Component
public class ApiKeyFeatureToggle {

  private static final String TOGGLE_FILE = "api-keys-enabled";

  private final FileStoreService fileStore;
  private final AtomicBoolean enabled;

  /**
   * Constructs an instance of {@code ApiKeyFeatureToggle}. Initializes the feature toggle by
   * determining the current state from a persisted file. Logs the initialized state of the toggle
   * upon creation.
   *
   * @param fileStore The {@code FileStoreService} instance used to resolve the persisted state
   *     file. Must not be {@code null}.
   */
  public ApiKeyFeatureToggle(FileStoreService fileStore) {
    this.fileStore = fileStore;
    this.enabled = new AtomicBoolean(readPersistedState());
    log.info("API key feature toggle initialized: enabled={}", enabled.get());
  }

  /**
   * Checks whether the API key feature toggle is currently enabled.
   *
   * @return {@code true} if the API key feature is enabled, {@code false} otherwise.
   */
  public boolean isEnabled() {
    return enabled.get();
  }

  /**
   * Enables the API key feature toggle.
   *
   * <p>Sets the internal state to {@code true}, persists the updated state to the designated
   * storage, and logs a confirmation message indicating that the feature has been enabled.
   *
   * <p>This method updates both the runtime and persisted state to ensure the feature toggle
   * remains enabled across application restarts.
   */
  public void enable() {
    enabled.set(true);
    persistState(true);
    log.info("API key feature ENABLED");
  }

  /**
   * Disables the API key feature toggle.
   *
   * <p>Updates the internal state to {@code false}, persists this updated state to the storage
   * managed by the {@code FileStoreService} to ensure the state remains disabled across application
   * restarts, and logs a message indicating that the feature has been disabled.
   */
  public void disable() {
    enabled.set(false);
    persistState(false);
    log.info("API key feature DISABLED");
  }

  /**
   * Toggles the current state of the API key feature.
   *
   * <p>This method flips the current state of the feature toggle (enabled/disabled), persists the
   * updated state to ensure consistency across application restarts, and logs the updated state for
   * tracking purposes.
   */
  public void toggle() {
    final boolean newState = !enabled.get();
    enabled.set(newState);
    persistState(newState);
    log.info("API key feature toggled: enabled={}", newState);
  }

  private boolean readPersistedState() {
    try {
      final Path file = fileStore.resolve(FileStoreLayout.AUTH, TOGGLE_FILE);
      if (Files.exists(file)) {
        final String content = Files.readString(file).trim();
        return "true".equalsIgnoreCase(content);
      }
    } catch (Exception e) {
      log.debug("Could not read API key toggle state, defaulting to disabled", e);
    }
    return false;
  }

  private void persistState(boolean state) {
    try {
      final Path file = fileStore.resolve(FileStoreLayout.AUTH, TOGGLE_FILE);
      Files.createDirectories(file.getParent());
      Files.writeString(file, String.valueOf(state));
    } catch (IOException e) {
      log.warn("Failed to persist API key toggle state", e);
    }
  }
}
