package live.omnisource.tessera.workspace.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceDto(
        @NotBlank String name
) {
}
