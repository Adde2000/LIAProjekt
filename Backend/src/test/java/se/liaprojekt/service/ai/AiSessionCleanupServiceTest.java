package se.liaprojekt.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
    void shouldCleanupOldSessions() {

        // Arrange
        AiSession session1 = new AiSession();
        session1.setThreadId("thread-1");

        AiSession session2 = new AiSession();
        session2.setThreadId("thread-2");

        when(repo.findByLastUsedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(session1, session2));

        // Act
        service.cleanupOldSessions();

        // Assert
        verify(client).deleteThread("thread-1");
        verify(client).deleteThread("thread-2");

        verify(repo).delete(session1);
        verify(repo).delete(session2);
    }

    @Test
    void shouldContinueCleanupWhenAzureDeleteFails() {

        // Arrange
        AiSession session = new AiSession();
        session.setThreadId("broken-thread");

        when(repo.findByLastUsedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(session));

        doThrow(new RuntimeException("Azure error"))
                .when(client)
                .deleteThread("broken-thread");

        // Act
        service.cleanupOldSessions();

        // Assert
        verify(client).deleteThread("broken-thread");

        // Viktigt:
        // session ska fortfarande tas bort lokalt
        verify(repo).delete(session);
    }

    @Test
    void shouldDoNothingWhenNoOldSessionsExist() {

        // Arrange
        when(repo.findByLastUsedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        service.cleanupOldSessions();

        // Assert
        verify(client, never()).deleteThread(any());
        verify(repo, never()).delete(any());
    }
}