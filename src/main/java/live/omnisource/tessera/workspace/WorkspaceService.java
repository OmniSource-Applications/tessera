package live.omnisource.tessera.workspace;

import live.omnisource.tessera.datastore.dto.DataStoreDto;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import live.omnisource.tessera.exceptions.WorkspaceAlreadyExistsException;
import live.omnisource.tessera.exceptions.WorkspaceNotFoundException;
import live.omnisource.tessera.exceptions.WorkspaceValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.workspace.dto.WorkspaceDto;
import live.omnisource.tessera.workspace.dto.WorkspaceRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class WorkspaceService {
    private static final String DATA_STORES = "data";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    private final FileStoreService fileStoreService;

    public WorkspaceService(FileStoreService fileStoreService) {
        this.fileStoreService = fileStoreService;
    }

    public void createWorkspace(WorkspaceDto workspaceDto) {
        final String workspaceName = normalizeName(workspaceDto.name());
        final Path workspaceDir = workspaceDir(workspaceName);
        if (Files.exists(workspaceDir)) {
            throw new WorkspaceAlreadyExistsException(workspaceDir.toString());
        }

        createWorkspaceDirectories(workspaceDir);
    }

    public List<String> listWorkspaces() {
        final Path workspaceDir = fileStoreService.resolve(FileStoreLayout.WORKSPACES);
        if (!Files.isDirectory(workspaceDir)) {
            return List.of();
        }

        try (Stream<Path> s = Files.list(workspaceDir)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new WorkspaceValidationException(e.getMessage());
        }
    }

    public int countWorkspaces() {
        return listWorkspaces().size();
    }

    public WorkspaceRecord getWorkspace(WorkspaceDto workspaceDto) {
        final Path dir = workspaceDir(workspaceDto.name());
        if (!Files.exists(dir)) throw new WorkspaceNotFoundException(workspaceDto.name());
        try (Stream<Path> s = Files.list(dir)) {
            Optional<List<String>> dataStores = Optional.of(s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList());

            return new WorkspaceRecord(workspaceDto.name(), dataStores);
        } catch (IOException e) {
            throw new WorkspaceValidationException(e.getMessage());
        }
    }

    public void deleteWorkspace(WorkspaceDto workspaceDto) {
        final Path dir = workspaceDir(workspaceDto.name());
        if (!Files.exists(dir)) return;

        try(Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {}
                    });
        } catch (IOException e) {}
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            throw new WorkspaceValidationException("workspace is required");
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new WorkspaceValidationException("workspace is required");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new WorkspaceValidationException(
                    "Invalid workspace name '" + name + "'. Use [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}."
            );
        }
        return name;
    }

    private Path workspaceDir(String workspace) {
        return workspaceRoot().resolve(normalizeName(workspace)).normalize();
    }

    private Path workspaceRoot() {
        return fileStoreService.resolve(FileStoreLayout.WORKSPACES);
    }

    private void createWorkspaceDirectories(Path workspaceDir) {
        try {
            log.info("Creating workspace: {}", workspaceDir);
            Files.createDirectories(workspaceDir.resolve(DATA_STORES));
        } catch (IOException e) {
            throw new WorkspaceValidationException(e.getMessage());
        }
    }
}
