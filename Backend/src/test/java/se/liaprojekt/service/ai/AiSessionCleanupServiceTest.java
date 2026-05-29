package se.liaprojekt.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.model.AiSession;
import se.liaprojekt.model.Course;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSessionCleanupServiceTest {

    @Mock
    private AiSessionRepository repo;

    @Mock
    private AzureAssistantClient client;

    @InjectMocks
    private AiSessionCleanupService service;

    @Test
    void shouldCleanupExpiredSessions() {

        // =========================
        // EXPIRED SESSION
        // =========================
        Course course = new Course();
        course.setAiSessionTtlWeeks(6);

        AiSession session1 = new AiSession();
        session1.setThreadId("thread-1");
        session1.setCourse(course);
        session1.setLastUsedAt(
                LocalDateTime.now().minusWeeks(7)
        );

        AiSession session2 = new AiSession();
        session2.setThreadId("thread-2");
        session2.setCourse(course);
        session2.setLastUsedAt(
                LocalDateTime.now().minusWeeks(8)
        );

        when(repo.findAll())
                .thenReturn(List.of(session1, session2));

        // =========================
        // ACT
        // =========================
        service.cleanupOldSessions();

        // =========================
        // ASSERT
        // =========================
        verify(client).deleteThread("thread-1");
        verify(client).deleteThread("thread-2");

        verify(repo).delete(session1);
        verify(repo).delete(session2);
    }

    @Test
    void shouldNotDeleteActiveSessions() {

        // =========================
        // ACTIVE SESSION
        // =========================
        Course course = new Course();
        course.setAiSessionTtlWeeks(6);

        AiSession session = new AiSession();
        session.setThreadId("active-thread");
        session.setCourse(course);
        session.setLastUsedAt(
                LocalDateTime.now().minusWeeks(2)
        );

        when(repo.findAll())
                .thenReturn(List.of(session));

        // =========================
        // ACT
        // =========================
        service.cleanupOldSessions();

        // =========================
        // ASSERT
        // =========================
        verify(client, never())
                .deleteThread(any());

        verify(repo, never())
                .delete(any());
    }

    @Test
    void shouldContinueCleanupWhenAzureDeleteFails() {

        // =========================
        // EXPIRED SESSION
        // =========================
        Course course = new Course();
        course.setAiSessionTtlWeeks(6);

        AiSession session = new AiSession();
        session.setThreadId("broken-thread");
        session.setCourse(course);
        session.setLastUsedAt(
                LocalDateTime.now().minusWeeks(10)
        );

        when(repo.findAll())
                .thenReturn(List.of(session));

        doThrow(new RuntimeException("Azure error"))
                .when(client)
                .deleteThread("broken-thread");

        // =========================
        // ACT
        // =========================
        service.cleanupOldSessions();

        // =========================
        // ASSERT
        // =========================
        verify(client)
                .deleteThread("broken-thread");

        // Session ska fortfarande tas bort lokalt
        verify(repo)
                .delete(session);
    }

    @Test
    void shouldDoNothingWhenNoSessionsExist() {

        when(repo.findAll())
                .thenReturn(List.of());

        // =========================
        // ACT
        // =========================
        service.cleanupOldSessions();

        // =========================
        // ASSERT
        // =========================
        verify(client, never())
                .deleteThread(any());

        verify(repo, never())
                .delete(any());
    }
}