package dramabot.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dramabot.hibernate.bootstrap.model.CatalogEntry;

public interface CatalogRepository extends JpaRepository<CatalogEntry, Long> {

}
