package se.liaprojekt.service.ai;

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

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;

    @Scheduled(fixedRate = 3600000)
    public void cleanupOldSessions() {

        List<AiSession> allSessions = repo.findAll();

        int deleted = 0;

        for (AiSession session : allSessions) {

            Integer ttlWeeks = session.getCourse()
                    .getAiSessionTtlWeeks();

            if (ttlWeeks == null) {
                ttlWeeks = 6;
            }

            LocalDateTime cutoff = LocalDateTime.now()
                    .minusWeeks(ttlWeeks);

            if (session.getLastUsedAt().isAfter(cutoff)) {
                continue;
            }

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
            deleted++;
        }

        log.info("Deleted {} expired AI sessions", deleted);
    }
}