package live.omnisource.tessera.filestore;

import live.omnisource.tessera.exceptions.FileStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Service class for managing the file storage system. This class provides methods
 * to initialize the file store structure, resolve file paths, write data atomically,
 * and manage specific directories.
 */
@Service
public class FileStoreService {
    private static final Logger log = LoggerFactory.getLogger(FileStoreService.class);
    private static final Path baseDir = Path.of(FileStoreLayout.ROOT).normalize();

    /**
     * Constructs an instance of the FileStoreService.
     * The constructor initializes the file storage service and logs information
     * about the initialization of the root data directory. No additional setup
     * logic is performed in this constructor.
     */
    public FileStoreService() {
        log.info("data_dir initialized");
    }

    /**
     * Initializes the file storage service by creating necessary directories
     * for services, network, database, authentication, secrets, RBAC, security,
     * catalog, and workspaces.html. This method ensures that the file structure is
     * properly set up for Tessera's file-based storage operations.
     */
    public void init() {
        log.info("Bootstrapping Tessera File Store with context path {}", baseDir);
        try {
            Files.createDirectories(resolve(FileStoreLayout.SERVICES));
            Files.createDirectories(resolve(FileStoreLayout.NETWORK));
            Files.createDirectories(resolve(FileStoreLayout.DATABASE));
            Files.createDirectories(resolve(FileStoreLayout.AUTH));
            Files.createDirectories(resolve(FileStoreLayout.SECRETS));
            Files.createDirectories(resolve(FileStoreLayout.RBAC));
            Files.createDirectories(resolve(FileStoreLayout.SECURITY));
            Files.createDirectories(resolve(FileStoreLayout.CATALOG));
            Files.createDirectories(resolve(FileStoreLayout.WORKSPACES));
            Files.createDirectories(resolve(FileStoreLayout.DATA_STORES));
            Files.createDirectories(resolve(FileStoreLayout.LAYER_GROUPS));
            Files.createDirectories(resolve(FileStoreLayout.CACHE));
            Files.createDirectories(resolve(FileStoreLayout.LOG));
            Files.createDirectories(resolve(FileStoreLayout.TEMP));
            Files.createDirectories(resolve(FileStoreLayout.RUN));
            Files.createDirectories(resolve(FileStoreLayout.SHARE_DEFAULTS));
            Files.createDirectories(resolve(FileStoreLayout.SHARE_SCHEMAS));

        } catch (IOException e) {
            log.error("Failed to create data_dir", e);
        }
    }

    /**
     * Returns the path to the secrets directory within the file store.
     *
     * @return Path to the secrets directory
     */
    public Path secretsDir() {
        final Path secretsDir = baseDir.resolve(FileStoreLayout.SECRETS);
        log.debug("Resolving secrets dir: {}", secretsDir);
        return secretsDir;
    }

    /**
     * Writes the provided byte array to the specified file on disk atomically.
     * This method ensures the write operation is completed without partial writes
     * by utilizing a temporary file and atomic move operations.
     *
     * @param target The destination {@code Path} where the data will be written.
     *               Must not be {@code null}. The parent directory of the target
     *               will be created if it does not exist.
     * @param bytes  The byte array to write to the target file. Must not be {@code null}.
     *
     * @throws FileStoreException If the write operation fails for any reason,
     *                            such as an I/O error or inability to move the temporary file.
     */
    public void writeAtomic(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());

            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

            Files.write(tmp, bytes);

            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            Files.move(
                    tmp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            try (FileChannel dirChannel = FileChannel.open(target.getParent(), StandardOpenOption.READ)) {
                dirChannel.force(true);
            }

            log.debug("Successfully atomically wrote to {}", target);

        } catch (IOException e) {
            log.error("Failed to write atomically to {}", target, e);
            throw new FileStoreException("Failed to write atomically to " + target, e);
        }
    }



    /**
     * Resolves a given file path relative to the base directory of the file store.
     * This method ensures that the resulting path is normalized and remains within
     * the boundaries of the base directory. If the resolved path escapes the base
     * directory, an exception is thrown to prevent unauthorized access or potential
     * security issues.
     *
     * @param first The first part of the file path to resolve.
     * @param more  Additional parts of the file path to resolve.
     * @return The resolved, normalized {@code Path} object within the base directory.
     * @throws FileStoreException If the resolved path is outside the base directory.
     */
    public Path resolve(String first, String ... more) {
        Path p = baseDir.resolve(Path.of(first, more)).normalize();
        if (!p.startsWith(baseDir)) {
            log.warn("Resolved path escapes baseDir: {}", p);
            throw new FileStoreException("Resolved path escapes baseDir: " + p);
        }
        return p;
    }
}
