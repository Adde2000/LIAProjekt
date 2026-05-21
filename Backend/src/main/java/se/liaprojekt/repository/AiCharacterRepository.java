package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.AiCharacter;

import java.util.List;

public interface AiCharacterRepository extends JpaRepository<AiCharacter, Long> {
    List<AiCharacter> findByCourses_Id(Long courseId);
}
