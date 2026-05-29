package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.Section;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    Optional<Section> findByCourseIdAndOrderIndex(Long courseId, int orderIndex);

    List<Section> findByCourseId(Long courseId);
}