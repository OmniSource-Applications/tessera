package live.omnisource.tessera.support;

import live.omnisource.tessera.filestore.FileStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Base class for tests that need a temporary data_dir file system.
 * Provides a fresh temporary directory before each test and cleans up after.
 */
public abstract class TempFileStoreBase {

    @TempDir
    protected Path tempDir;

    protected Path dataDir;

    @BeforeEach
    void setUpFileStore() throws IOException {
        dataDir = tempDir.resolve("data_dir");
        Files.createDirectories(dataDir);
        // Override the static ROOT — tests should use dataDir directly
        System.setProperty("tessera.data.dir", dataDir.toString());
    }

    @AfterEach
    void tearDownFileStore() throws IOException {
        System.clearProperty("tessera.data.dir");
    }

    /**
     * Create a directory structure relative to dataDir.
     */
    protected Path mkdir(String... segments) throws IOException {
        Path dir = dataDir;
        for (String s : segments) dir = dir.resolve(s);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Write a file relative to dataDir.
     */
    protected Path writeFile(Path dir, String filename, String content) throws IOException {
        Path file = dir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }
}