package live.omnisource.tessera.workspace.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRecord(
        @NotBlank String name
) {
}
