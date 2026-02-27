package live.omnisource.tessera.layer;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.datasource.connector.DataSourceConnector;
import live.omnisource.tessera.exceptions.DataStoreNotFoundException;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.layer.dto.LayerRecord;
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
public class LayerService {

    private static final String METADATA_FILE = "layer.json";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private final FileStoreService fileStoreService;
    private final Map<String, DataSourceConnector> connectionFactories;
    private final ObjectMapper objectMapper;

    public LayerService(FileStoreService fileStoreService,
                        Map<String, DataSourceConnector> connectionFactories,
                        ObjectMapper objectMapper) {
        this.fileStoreService = fileStoreService;
        this.connectionFactories = connectionFactories;
        this.objectMapper = objectMapper;
    }

    // ── Introspection ───────────────────────────────────────

    /**
     * Introspect a data store to discover available spatial tables.
     * Reads the datastore.json to determine the type, then delegates
     * to the appropriate connector.
     */
    public IntrospectionResult introspect(String workspace, String datastore) {
        // Read the datastore metadata to get the type
        Path metadataFile = dataStoreDir(workspace, datastore).resolve("datastore.json");
        if (!Files.exists(metadataFile)) {
            throw new DataStoreNotFoundException(datastore);
        }

        try {
            var dsRecord = objectMapper.readTree(Files.readAllBytes(metadataFile));
            String type = dsRecord.get("type").asText();

            DataSourceConnector connector = connectionFactories.get(type.toLowerCase());
            if (connector == null) {
                throw new DataStoreValidationException("Unsupported type: " + type);
            }

            String secretKey = "workspaces/" + workspace + "/datastores/" + datastore;
            return connector.introspect(secretKey);

        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to read datastore metadata: " + e.getMessage());
        }
    }

    // ── Layer CRUD ──────────────────────────────────────────

    /**
     * Create a layer from an introspected spatial table.
     */
    public void createLayer(LayerDto dto, IntrospectionResult.SpatialTable table) {
        Path layerDir = layerDir(dto);

        if (Files.exists(layerDir)) {
            throw new DataStoreValidationException("Layer '" + dto.layer() + "' already exists.");
        }

        LayerRecord record = LayerRecord.fromIntrospection(dto, table);

        try {
            Files.createDirectories(layerDir);
            byte[] json = objectMapper.writeValueAsBytes(record);
            fileStoreService.writeAtomic(layerDir.resolve(METADATA_FILE), json);
            log.info("Created layer '{}/{}/{}'", dto.workspace(), dto.datastore(), dto.layer());
        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to write layer metadata: " + e.getMessage());
        }

    }

    /**
     * List all layer names within a data store.
     */
    public List<String> listLayers(String workspace, String datastore) {
        Path dir = layersRoot(workspace, datastore);
        if (!Files.isDirectory(dir)) return List.of();

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
     * Read a single layer's metadata.
     */
    public LayerRecord getLayer(LayerDto dto) {
        Path metadataFile = layerDir(dto).resolve(METADATA_FILE);
        if (!Files.exists(metadataFile)) {
            throw new DataStoreValidationException("Layer not found: " + dto.layer());
        }
        try {
            return objectMapper.readValue(Files.readAllBytes(metadataFile), LayerRecord.class);
        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to read layer: " + e.getMessage());
        }
    }

    /**
     * Delete a layer and its metadata.
     */
    public void deleteLayer(LayerDto dto) {
        Path dir = layerDir(dto);
        if (!Files.exists(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
            log.info("Deleted layer '{}/{}/{}'", dto.workspace(), dto.datastore(), dto.layer());
        } catch (IOException e) {
            throw new DataStoreValidationException("Failed to delete layer: " + e.getMessage());
        }
    }

    public List<LayerDto> listAllLayers() {
        final Path workspacesRoot = fileStoreService.resolve(FileStoreLayout.WORKSPACES);
        if (!Files.isDirectory(workspacesRoot)) {
            return List.of();
        }

        try (Stream<Path> workspaces = Files.list(workspacesRoot)) {
            return workspaces
                    .filter(Files::isDirectory)
                    .flatMap(ws -> {
                        final String wsName = ws.getFileName().toString();

                        final Path dataDir = ws.resolve("data");
                        if (!Files.isDirectory(dataDir)) {
                            return Stream.empty();
                        }

                        // Scan datastores under <ws>/data/<datastore>/
                        try (Stream<Path> datastores = Files.list(dataDir)) {
                            // IMPORTANT: return a Stream from inside try-with-resources
                            // by collecting the results first (or by not closing early).
                            // We'll collect per-workspace to avoid leaking a closed stream.
                            return datastores
                                    .filter(Files::isDirectory)
                                    .flatMap(ds -> {
                                        final String dsName = ds.getFileName().toString();

                                        final Path layersDir = ds.resolve("layers");
                                        if (!Files.isDirectory(layersDir)) {
                                            return Stream.empty();
                                        }

                                        try {
                                            return Files.list(layersDir)
                                                    .filter(Files::isDirectory)
                                                    .map(layerDir -> new LayerDto(
                                                            wsName,
                                                            dsName,
                                                            layerDir.getFileName().toString()
                                                    ));
                                        } catch (IOException e) {
                                            // Match your datastore behavior: ignore unreadable subtree
                                            return Stream.empty();
                                        }
                                    })
                                    .toList()   // materialize before datastores stream closes
                                    .stream();
                        } catch (IOException e) {
                            // Match your datastore behavior: ignore unreadable workspace subtree
                            return Stream.empty();
                        }
                    })
                    .sorted(Comparator.comparing(LayerDto::workspace)
                            .thenComparing(LayerDto::datastore)
                            .thenComparing(LayerDto::layer))
                    .toList();

        } catch (IOException e) {
            throw new DataStoreValidationException(e.getMessage());
        }
    }

    public int countAllLayers() {
        return listAllLayers().size();
    }

    // ── Path helpers ────────────────────────────────────────

    private Path workspaceDir(String workspace) {
        return fileStoreService.resolve(FileStoreLayout.WORKSPACES).resolve(workspace).normalize();
    }

    private Path dataStoreDir(String workspace, String datastore) {
        return workspaceDir(workspace).resolve("data").resolve(datastore).normalize();
    }

    private Path layersRoot(String workspace, String datastore) {
        return dataStoreDir(workspace, datastore).resolve("layers");
    }

    private Path layerDir(LayerDto dto) {
        return layersRoot(dto.workspace(), dto.datastore())
                .resolve(normalizeName(dto.layer())).normalize();
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DataStoreValidationException("Layer name is required.");
        }
        String name = raw.trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new DataStoreValidationException(
                    "Invalid layer name '" + name + "'. Use [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}."
            );
        }
        return name;
    }
}