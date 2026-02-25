package live.omnisource.tessera.repository;

import live.omnisource.tessera.model.entity.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExternalSourceRepository extends JpaRepository<ExternalSource, UUID> {
}
