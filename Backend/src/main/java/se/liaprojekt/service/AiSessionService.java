package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

@Service
@RequiredArgsConstructor
public class AiSessionService {

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;

    // CREATE SESSION = CREATE THREAD
    public AiSession createSession() {

        String threadId = client.createThread();

        AiSession session = new AiSession();
        session.setThreadId(threadId);

        return repo.save(session);
    }

    public AiSession get(Long id) {
        return repo.findById(id).orElseThrow();
    }
}