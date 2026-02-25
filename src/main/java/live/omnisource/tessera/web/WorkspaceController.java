package live.omnisource.tessera.web;

import live.omnisource.tessera.workspace.dto.CreateWorkspaceRecord;
import live.omnisource.tessera.workspace.WorkspaceRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller("/workspace")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceController(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @GetMapping("/new")
    public String newWorkspaceForm() {
        return "forms/workspace/new";
    }

    @PostMapping
    public void createWorkspace(@RequestParam String name) {
        workspaceRepository.create(new CreateWorkspaceRecord(name));

    }

    @GetMapping("/{workspace}")
    public String getWorkspace(@PathVariable String workspace) {
        return "pages/workspace/panel";
    }
}
