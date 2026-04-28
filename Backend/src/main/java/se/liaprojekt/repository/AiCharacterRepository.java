package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.AiCharacter;

public interface AiCharacterRepository extends JpaRepository<AiCharacter, Long> {
}
