package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;

    public String chat(Long sessionId, String message) {

        AiSession session = repo.findById(sessionId)
                .orElseThrow();

        String threadId = session.getThreadId();

        client.addMessage(threadId, message);

        String runId = client.createRun(
                threadId,
                session.getAiCharacter().getAssistantId()
        );

        session.setLastUsedAt(LocalDateTime.now());
        repo.save(session);

        return client.waitForCompletion(threadId, runId);
    }
}