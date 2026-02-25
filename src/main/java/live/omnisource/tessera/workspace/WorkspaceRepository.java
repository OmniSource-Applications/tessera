package live.omnisource.tessera.workspace;

import live.omnisource.tessera.exceptions.WorkspaceAlreadyExistsException;
import live.omnisource.tessera.exceptions.WorkspaceValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.workspace.dto.CreateWorkspaceRecord;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Repository
public class WorkspaceRepository {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    private final FileStoreService fileStoreService;

    public WorkspaceRepository(FileStoreService fileStoreService) {
        this.fileStoreService = fileStoreService;
    }

    public Path workspacesRoot() {
        return fileStoreService.resolve(FileStoreLayout.WORKSPACES);
    }

    public Path workspaceDir(String workspace) {
        return workspacesRoot().resolve(normalizeName(workspace)).normalize();
    }

    public void create(CreateWorkspaceRecord createWorkspaceRecord) {
        final String name = normalizeName(createWorkspaceRecord.name());
        Path dir = workspaceDir(name);
        if (Files.exists(dir)) {
            throw new WorkspaceAlreadyExistsException(name);
        }
        createWorkspaceDir(dir);
    }

    private void createWorkspaceDir(Path workspaceDir) {
        try {
            Files.createDirectories(workspaceDir);
        } catch (IOException e) {
            throw new WorkspaceValidationException("Failed creating workspace: " + e.getMessage());
        }
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
}
