package live.omnisource.tessera.unit.workspace;

import live.omnisource.tessera.exceptions.WorkspaceAlreadyExistsException;
import live.omnisource.tessera.exceptions.WorkspaceNotFoundException;
import live.omnisource.tessera.exceptions.WorkspaceValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.workspace.WorkspaceService;
import live.omnisource.tessera.workspace.dto.WorkspaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkspaceServiceTest {

    @TempDir
    Path tempDir;

    FileStoreService fileStore;
    WorkspaceService service;

    @BeforeEach
    void setUp() throws Exception {
        fileStore = mock(FileStoreService.class);
        Path workspacesDir = tempDir.resolve("etc/catalog/workspaces");
        Files.createDirectories(workspacesDir);

        when(fileStore.resolve(FileStoreLayout.WORKSPACES)).thenReturn(workspacesDir);
        // resolve(WORKSPACES) is called by the service for workspace root
        service = new WorkspaceService(fileStore);
    }

    @Test
    void createWorkspace_createsDirectory() {
        service.createWorkspace(new WorkspaceDto("myworkspace"));
        assertThat(service.listWorkspaces()).contains("myworkspace");
    }

    @Test
    void createWorkspace_rejectsDuplicate() {
        service.createWorkspace(new WorkspaceDto("dupws"));
        assertThatThrownBy(() -> service.createWorkspace(new WorkspaceDto("dupws")))
                .isInstanceOf(WorkspaceAlreadyExistsException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "123bad"})
    void createWorkspace_rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> service.createWorkspace(new WorkspaceDto(name)))
                .isInstanceOf(WorkspaceValidationException.class);
    }

    @Test
    void getWorkspace_returnsExisting() {
        service.createWorkspace(new WorkspaceDto("getme"));
        var ws = service.getWorkspace(new WorkspaceDto("getme"));
        assertThat(ws.name()).isEqualTo("getme");
    }

    @Test
    void getWorkspace_throwsForMissing() {
        assertThatThrownBy(() -> service.getWorkspace(new WorkspaceDto("nope")))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void deleteWorkspace_removesDirectory() {
        service.createWorkspace(new WorkspaceDto("todelete"));
        assertThat(service.listWorkspaces()).contains("todelete");

        service.deleteWorkspace(new WorkspaceDto("todelete"));
        assertThat(service.listWorkspaces()).doesNotContain("todelete");
    }

    @Test
    void listWorkspaces_returnsSorted() {
        service.createWorkspace(new WorkspaceDto("charlie"));
        service.createWorkspace(new WorkspaceDto("alpha"));
        service.createWorkspace(new WorkspaceDto("bravo"));

        assertThat(service.listWorkspaces())
                .containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void countWorkspaces_returnsCorrectCount() {
        service.createWorkspace(new WorkspaceDto("ws1"));
        service.createWorkspace(new WorkspaceDto("ws2"));
        assertThat(service.countWorkspaces()).isEqualTo(2);
    }
}