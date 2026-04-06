package live.omnisource.tessera.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import live.omnisource.tessera.model.entity.ExternalSource;

public interface ExternalSourceRepository extends JpaRepository<ExternalSource, UUID> {}
