package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.AiSession;

import java.time.LocalDateTime;
import java.util.List;

public interface AiSessionRepository extends JpaRepository<AiSession, Long> {

    List<AiSession> findAllByUser_IdAndCourse_Id(
            Long userId,
            Long courseId
    );

    List<AiSession> findByLastUsedAtBefore(
            LocalDateTime cutoff
    );
}
