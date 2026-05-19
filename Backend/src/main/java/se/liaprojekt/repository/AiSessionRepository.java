package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.AiSession;

import java.util.Optional;

public interface AiSessionRepository extends JpaRepository<AiSession, Long> {

    Optional<AiSession> findByUser_IdAndCourse_IdAndAiCharacter_Id(
            Long userId,
            Long courseId,
            Long characterId
    );
}
