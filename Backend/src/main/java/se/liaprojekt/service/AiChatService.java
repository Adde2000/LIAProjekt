package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int MAX_MESSAGE_LENGTH = 5000;

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;

    public String chat(Long sessionId, String message) {

        // =========================
        // VALIDATE MESSAGE
        // =========================

        if (message == null || message.isBlank()) {
            throw new BadRequestException(
                    "Message cannot be empty"
            );
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException(
                    "Message exceeds max length of "
                            + MAX_MESSAGE_LENGTH
                            + " characters"
            );
        }

        // =========================
        // GET SESSION
        // =========================

        AiSession session = repo.findById(sessionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "AI session not found"
                        )
                );

        // =========================
        // SEND MESSAGE
        // =========================

        String threadId = session.getThreadId();

        client.addMessage(threadId, message);

        // =========================
        // CREATE RUN
        // =========================

        String runId = client.createRun(
                threadId,
                session.getAiCharacter().getAssistantId()
        );

        // =========================
        // UPDATE SESSION
        // =========================

        session.setLastUsedAt(LocalDateTime.now());
        repo.save(session);

        // =========================
        // WAIT FOR RESPONSE
        // =========================

        return client.waitForCompletion(
                threadId,
                runId
        );
    }
}