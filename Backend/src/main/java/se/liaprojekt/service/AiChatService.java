package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

/**
 * Handles chat flow using Azure Assistants (Threads)
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;

    public String chat(Long sessionId, String message) {

        AiSession session = repo.findById(sessionId)
                .orElseThrow();

        String threadId = session.getThreadId();

        // 1. send message
        client.addMessage(threadId, message);

        // 2. IMPORTANT: run with correct assistant
        return client.runAndWaitForResponse(
                threadId,
                session.getAiCharacter().getAssistantId()
        );
    }
}