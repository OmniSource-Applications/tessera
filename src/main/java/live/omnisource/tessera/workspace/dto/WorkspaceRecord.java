package live.omnisource.tessera.workspace.dto;

import java.util.List;
import java.util.Optional;

public record WorkspaceRecord(
        String name,
        Optional<List<String>> layers,
        Optional<List<String>> dataSources
) {
}
