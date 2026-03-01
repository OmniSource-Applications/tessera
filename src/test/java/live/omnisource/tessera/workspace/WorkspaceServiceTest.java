package live.omnisource.tessera.workspace;

import live.omnisource.tessera.exceptions.WorkspaceAlreadyExistsException;
import live.omnisource.tessera.exceptions.WorkspaceNotFoundException;
import live.omnisource.tessera.exceptions.WorkspaceValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.workspace.dto.WorkspaceDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.*;

class WorkspaceServiceTest {

    private final FileStoreService fileStoreService = new FileStoreService();
    private final WorkspaceService workspaceService = new WorkspaceService(fileStoreService);

    @AfterEach
    void cleanup() throws IOException {
        Path dataDir = Path.of(FileStoreLayout.ROOT).normalize();
        if (Files.exists(dataDir)) {
            try (var walk = Files.walk(dataDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void createListGetDelete_workspaceLifecycle() {
        fileStoreService.init();

        workspaceService.createWorkspace(new WorkspaceDto("alpha"));
        assertThat(workspaceService.listWorkspaces()).contains("alpha");
        assertThat(workspaceService.countWorkspaces()).isEqualTo(1);

        var record = workspaceService.getWorkspace(new WorkspaceDto("alpha"));
        assertThat(record.name()).isEqualTo("alpha");
        assertThat(record.dataSources()).isPresent();

        workspaceService.deleteWorkspace(new WorkspaceDto("alpha"));
        assertThat(workspaceService.listWorkspaces()).doesNotContain("alpha");
    }

    @Test
    void createWorkspace_rejectsInvalidName() {
        assertThatThrownBy(() -> workspaceService.createWorkspace(new WorkspaceDto("..")))
                .isInstanceOf(WorkspaceValidationException.class);
    }

    @Test
    void createWorkspace_throwsIfExists() {
        fileStoreService.init();
        workspaceService.createWorkspace(new WorkspaceDto("alpha"));

        assertThatThrownBy(() -> workspaceService.createWorkspace(new WorkspaceDto("alpha")))
                .isInstanceOf(WorkspaceAlreadyExistsException.class);
    }

    @Test
    void getWorkspace_throwsIfMissing() {
        fileStoreService.init();
        assertThatThrownBy(() -> workspaceService.getWorkspace(new WorkspaceDto("missing")))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }
}
