package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.AiSession;

public interface AiSessionRepository extends JpaRepository<AiSession, Long> {
}
