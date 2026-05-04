package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.Material;

import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findBySectionId(Long sectionId);
}