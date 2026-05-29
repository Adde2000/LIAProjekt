package se.liaprojekt.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.ChatHistoryMessage;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;
import java.util.List;

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
        // GET ASSISTANT ID
        // =========================

        String assistantId = session
                .getCourse()
                .getAssistantId();

        if (assistantId == null || assistantId.isBlank()) {

            throw new BadRequestException(
                    "No assistant assigned to course"
            );
        }

        // =========================
        // SEND MESSAGE
        // =========================

        String threadId = session.getThreadId();

        client.addMessage(
                threadId,
                message
        );

        // =========================
        // ATTACH COURSE VECTOR STORE
        // =========================

        String vectorStoreId = session
                .getCourse()
                .getVectorStoreId();

        if (vectorStoreId == null ||
                vectorStoreId.isBlank()) {

            throw new BadRequestException(
                    "Course has no vector store"
            );
        }

        client.attachVectorStoreToThread(
                threadId,
                vectorStoreId
        );

        // =========================
        // CREATE RUN
        // =========================

        String runId = client.createRun(
                threadId,
                session.getCourse().getAssistantId()
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

    // =========================
    // GET CHAT HISTORY
    // =========================

    public List<ChatHistoryMessage> getHistory(
            Long sessionId
    ) {

        AiSession session = repo.findById(sessionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "AI session not found"
                        )
                );

        return client.getThreadMessages(
                session.getThreadId()
        );
    }
}