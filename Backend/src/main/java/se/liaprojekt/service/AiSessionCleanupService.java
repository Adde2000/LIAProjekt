package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionCleanupService {

    private static final int SESSION_MAX_AGE_HOURS = 48;

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;

    @Scheduled(fixedRate = 3600000)
    public void cleanupOldSessions() {

        LocalDateTime cutoff =
                LocalDateTime.now()
                        .minusHours(SESSION_MAX_AGE_HOURS);

        List<AiSession> oldSessions =
                repo.findByLastUsedAtBefore(cutoff);

        log.info(
                "Found {} expired AI sessions",
                oldSessions.size()
        );

        for (AiSession session : oldSessions) {

            try {

                client.deleteThread(
                        session.getThreadId()
                );

            } catch (Exception ex) {

                log.warn(
                        "Failed deleting Azure thread {}",
                        session.getThreadId()
                );
            }

            repo.delete(session);
        }
    }
}