package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.AiCharacter;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiCharacterRepository;
import se.liaprojekt.repository.AiSessionRepository;

@Service
@RequiredArgsConstructor
public class AiSessionInitService {

    private final AzureAssistantClient client;
    private final AiSessionRepository sessionRepo;
    private final AiCharacterRepository characterRepo;

    /**
     * Creates a session bound to a specific AI character (dynamic assistant selection)
     */
    public AiSession createSession(Long userId, Long courseId, Long characterId) {

        // 1. fetch character
        AiCharacter character = characterRepo.findById(characterId)
                .orElseThrow();

        // 2. extract assistantId (THIS is the key)
        String assistantId = character.getAssistantId();

        // 3. create thread
        String threadId = client.createThread();

        // 4. save session with BOTH thread + assistant
        AiSession session = new AiSession();
        session.setThreadId(threadId);
        session.setAiCharacter(character);

        return sessionRepo.save(session);
    }
}