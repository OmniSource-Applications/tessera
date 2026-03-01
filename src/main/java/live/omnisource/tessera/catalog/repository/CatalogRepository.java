package live.omnisource.tessera.catalog.repository;

import live.omnisource.tessera.catalog.entity.CatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogRepository extends JpaRepository<CatalogEntry, UUID> {

    Optional<CatalogEntry> findByName(String name);

    List<CatalogEntry> findByCategoryOrderByNameAsc(String category);

    boolean existsByName(String name);
}