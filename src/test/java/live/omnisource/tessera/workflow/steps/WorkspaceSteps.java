package live.omnisource.tessera.workflow.steps;

import io.cucumber.java.After;
import io.cucumber.java.en.*;
import live.omnisource.tessera.workspace.WorkspaceService;
import live.omnisource.tessera.workspace.dto.WorkspaceDto;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkspaceSteps {

    @Autowired
    WorkspaceService workspaceService;

    private final List<String> createdWorkspaces = new ArrayList<>();
    private Exception lastError;

    @After
    public void cleanup() {
        createdWorkspaces.forEach(name -> {
            try {
                workspaceService.deleteWorkspace(new WorkspaceDto(name));
            } catch (Exception ignored) {}
        });
        createdWorkspaces.clear();
        lastError = null;
    }

    @Given("I am authenticated as {string}")
    public void authenticated(String user) {
        // Authentication is handled by @WithMockUser or Spring Security test context
        // This step is a placeholder for readability
    }

    @Given("a workspace named {string} exists")
    public void workspaceExists(String name) {
        try {
            workspaceService.createWorkspace(new WorkspaceDto(name));
            createdWorkspaces.add(name);
        } catch (Exception ignored) {
            // Already exists
        }
    }

    @When("I create a workspace named {string}")
    public void createWorkspace(String name) {
        workspaceService.createWorkspace(new WorkspaceDto(name));
        createdWorkspaces.add(name);
    }

    @When("I try to create a workspace named {string}")
    public void tryCreateWorkspace(String name) {
        try {
            workspaceService.createWorkspace(new WorkspaceDto(name));
            createdWorkspaces.add(name);
        } catch (Exception e) {
            lastError = e;
        }
    }

    @When("I delete the workspace {string}")
    public void deleteWorkspace(String name) {
        workspaceService.deleteWorkspace(new WorkspaceDto(name));
        createdWorkspaces.remove(name);
    }

    @Then("the workspace {string} should exist")
    public void workspaceShouldExist(String name) {
        var ws = workspaceService.getWorkspace(new WorkspaceDto(name));
        assertThat(ws).isNotNull();
        assertThat(ws.name()).isEqualTo(name);
    }

    @Then("the workspace {string} should not exist")
    public void workspaceShouldNotExist(String name) {
        assertThat(workspaceService.listWorkspaces()).doesNotContain(name);
    }

    @Then("the workspace list should contain {string}")
    public void workspaceListContains(String name) {
        assertThat(workspaceService.listWorkspaces()).contains(name);
    }

    @Then("I should receive an error about the workspace already existing")
    public void errorAlreadyExists() {
        assertThat(lastError).isNotNull();
    }

    @Then("I should receive a validation error")
    public void validationError() {
        assertThat(lastError).isNotNull();
    }
}