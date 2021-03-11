package dramabot.service.repository;

import dramabot.hibernate.bootstrap.model.CatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<CatalogEntry, Long> {

}
