package live.omnisource.tessera.datastore;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.datasource.connector.DataSourceConnector;
import live.omnisource.tessera.datastore.dto.DataStoreDto;
import live.omnisource.tessera.datastore.dto.DataStoreRecord;
import live.omnisource.tessera.exceptions.DataStoreAlreadyExistsException;
import live.omnisource.tessera.exceptions.DataStoreNotFoundException;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import live.omnisource.tessera.exceptions.WorkspaceNotFoundException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class DataStoreService {

    private static final String METADATA_FILE = "datastore.json";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private final FileStoreService fileStoreService;
    private final Map<String, DataSourceConnector> connectionFactories;
    private final ObjectMapper objectMapper;

    public DataStoreService(FileStoreService fileStoreService,
                            Map<String, DataSourceConnector> connectionFactories,
                            ObjectMapper objectMapper) {
        this.fileStoreService = fileStoreService;
        this.connectionFactories = connectionFactories;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a data store within a workspace.
     *
     * Flow:
     *   1. Validate workspace exists
     *   2. Validate data store name and check it doesn't already exist
     *   3. Resolve the appropriate connection factory by type
     *   4. Test the connection — on success, credentials are persisted by the factory
     *   5. Create the data store directory and write metadata
     *
     * @param dto         workspace + datastore name
     * @param credentials connection credentials to test and persist
     * @return ConnectionInfo with version info on success, or error details on failure
     */
    public DataSourceConnector.ConnectionInfo createDataStore(DataStoreDto dto, live.omnisource.tessera.model.dto.ExternalSourceCredentials credentials) {
        final String workspace = dto.workspace();
        final String datastore = normalizeName(dto.datastore());

        // 1. Workspace must exist
        Path workspaceDir = workspaceDir(workspace);
        if (!Files.isDirectory(workspaceDir)) {
            throw new WorkspaceNotFoundException(workspace);
        }

        // 2. Data store must not already exist
        Path dataStoreDir = dataStoreDir(workspace, datastore);
        if (Files.exists(dataStoreDir)) {
            throw new DataStoreAlreadyExistsException(datastore);
        }

        // 3. Resolve connection factory
        DataSourceConnector factory = resolveFactory(credentials.type());

        // 4. Test connection — factory persists credentials on success
        String sourceKey = sourceKey(workspace, datastore);
        DataSourceConnector.ConnectionInfo info = factory.testConnection(sourceKey, credentials);

        if (!info.connected()) {
            return info;
        }

        // 5. Create directory and write metadata
        try {
            Files.createDirectories(dataStoreDir);

            DataStoreRecord record = new DataStoreRecord(
                    dto,
                    credentials.type(),
                    credentials.url(),
                    credentials.poolSize(),
                    credentials.contactPoints(),
                    credentials.port(),
                    credentials.datacenter(),
                    credentials.keyspace(),
                    credentials.hosts(),
                    credentials.verifySsl()
            );

            byte[] json = objectMapper.writeValueAsBytes(record);
            fileStoreService.writeAtomic(dataStoreDir.resolve(METADATA_FILE), json);

            log.info("Created data store '{}/{}' [{}]", workspace, datastore, credentials.type());
        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to write data store metadata: " + e.getMessage());
        }

        return info;
    }

    /**
     * Lists all data store names within a workspace.
     */
    public List<String> listDataStores(String workspace) {
        Path dir = dataStoresRoot(workspace);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new DataStoreValidationException(e.getMessage());
        }
    }

    /**
     * Lists all data stores across all workspaces.
     * Returns pairs of (workspace, datastore name).
     */
    public List<DataStoreDto> listAllDataStores() {
        Path workspacesRoot = fileStoreService.resolve(FileStoreLayout.WORKSPACES);
        if (!Files.isDirectory(workspacesRoot)) return List.of();

        try (Stream<Path> workspaces = Files.list(workspacesRoot)) {
            return workspaces
                    .filter(Files::isDirectory)
                    .flatMap(ws -> {
                        String wsName = ws.getFileName().toString();
                        Path dataDir = ws.resolve("data");
                        if (!Files.isDirectory(dataDir)) return Stream.empty();
                        try {
                            return Files.list(dataDir)
                                    .filter(Files::isDirectory)
                                    .map(ds -> new DataStoreDto(wsName, ds.getFileName().toString()));
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .sorted(Comparator.comparing(DataStoreDto::workspace)
                            .thenComparing(DataStoreDto::datastore))
                    .toList();
        } catch (IOException e) {
            throw new DataStoreValidationException(e.getMessage());
        }
    }

    public int countAllDataStores() {
        return listAllDataStores().size();
    }

    /**
     * Reads the metadata for a single data store.
     */
    public DataStoreRecord getDataStore(DataStoreDto dto) {
        Path dir = dataStoreDir(dto.workspace(), dto.datastore());
        Path metadataFile = dir.resolve(METADATA_FILE);

        if (!Files.exists(metadataFile)) {
            throw new DataStoreNotFoundException(dto.datastore());
        }

        try {
            byte[] json = Files.readAllBytes(metadataFile);
            return objectMapper.readValue(json, DataStoreRecord.class);
        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to read data store metadata: " + e.getMessage());
        }
    }

    /**
     * Deletes a data store directory and its persisted credentials.
     */
    public void deleteDataStore(DataStoreDto dto) {
        Path dir = dataStoreDir(dto.workspace(), dto.datastore());
        if (!Files.exists(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", p, e);
                        }
                    });
            log.info("Deleted data store '{}/{}'", dto.workspace(), dto.datastore());
        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to delete data store: " + e.getMessage());
        }
    }

    /**
     * Tests a connection without persisting anything.
     */
    public DataSourceConnector.ConnectionInfo testConnection(ExternalSourceCredentials credentials) {
        DataSourceConnector factory = resolveFactory(credentials.type());
        // Use a throwaway key — factory implementations that take credentials
        // directly won't persist when using the two-arg overload without a real sourceKey
        return factory.testConnection("_test", credentials);
    }

    // --- internals ---

    private DataSourceConnector resolveFactory(String type) {
        if (type == null || type.isBlank()) {
            throw new DataStoreValidationException("Data store type is required.");
        }
        DataSourceConnector factory = connectionFactories.get(type.toLowerCase());
        if (factory == null) {
            throw new DataStoreValidationException("Unsupported data store type: " + type);
        }
        return factory;
    }

    /**
     * Secret key convention: workspaces/{workspace}/datastores/{datastore}
     */
    private String sourceKey(String workspace, String datastore) {
        return "workspaces/" + workspace + "/datastores/" + datastore;
    }

    private Path workspaceDir(String workspace) {
        return fileStoreService.resolve(FileStoreLayout.WORKSPACES).resolve(workspace).normalize();
    }

    private Path dataStoresRoot(String workspace) {
        return workspaceDir(workspace).resolve("data");
    }

    private Path dataStoreDir(String workspace, String datastore) {
        return dataStoresRoot(workspace).resolve(normalizeName(datastore)).normalize();
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            throw new DataStoreValidationException("Data store name is required.");
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new DataStoreValidationException("Data store name is required.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new DataStoreValidationException(
                    "Invalid data store name '" + name + "'. Use [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}."
            );
        }
        return name;
    }
}