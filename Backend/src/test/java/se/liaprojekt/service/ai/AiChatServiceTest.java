package se.liaprojekt.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.dto.ChatHistoryMessage;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.model.Course;
import se.liaprojekt.repository.AiSessionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private AiSessionRepository repo;

    @Mock
    private AzureAssistantClient client;

    @InjectMocks
    private AiChatService service;

    private AiSession session;
    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setAssistantId("assistant-123");
        course.setVectorStoreId("vs-123");

        session = new AiSession();
        session.setId(1L);
        session.setThreadId("thread-1");
        session.setCourse(course);
        session.setLastUsedAt(LocalDateTime.now());
    }

    @Test
    void shouldChatSuccessfully() {
        // Arrange
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(client.createRun("thread-1", "assistant-123")).thenReturn("run-1");
        when(client.waitForCompletion("thread-1", "run-1")).thenReturn("AI response");
        when(repo.save(any(AiSession.class))).thenReturn(session);

        // Act
        String result = service.chat(1L, "Hello AI");

        // Assert
        assertEquals("AI response", result);

        verify(client).addMessage("thread-1", "Hello AI");
        verify(client).createRun("thread-1", "assistant-123");
        verify(repo).save(session);
        verify(client).waitForCompletion("thread-1", "run-1");
    }

    @Test
    void shouldThrowWhenMessageIsNull() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.chat(1L, null)
        );

        assertEquals("Message cannot be empty", exception.getMessage());
        verifyNoInteractions(repo, client);
    }

    @Test
    void shouldThrowWhenMessageIsBlank() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.chat(1L, " ")
        );

        assertEquals("Message cannot be empty", exception.getMessage());
        verifyNoInteractions(repo, client);
    }

    @Test
    void shouldThrowWhenMessageTooLong() {
        // Arrange
        String longMessage = "a".repeat(5001);

        // Act + Assert
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.chat(1L, longMessage)
        );

        // FIX: Ändrad till den exakta strängen som din klass nu bygger upp dynamically
        assertEquals("Message exceeds max length of 5000 characters", exception.getMessage());
        verifyNoInteractions(repo, client);
    }

    @Test
    void shouldThrowWhenSessionNotFound() {
        // Arrange
        when(repo.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.chat(1L, "Hello")
        );

        assertEquals("AI session not found", exception.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void shouldThrowWhenAssistantIdMissing() {
        // Arrange
        course.setAssistantId(null); // Eller ""

        when(repo.findById(1L)).thenReturn(Optional.of(session));

        // Act + Assert
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.chat(1L, "Hello")
        );

        assertEquals("No assistant assigned to course", exception.getMessage());
        verifyNoInteractions(client);
        verify(repo, never()).save(any(AiSession.class));
    }

    // Validerar fallet där vector store saknas mitt i chat-metoden
    @Test
    void shouldThrowWhenVectorStoreMissingDuringChat() {
        // Arrange
        course.setVectorStoreId(null); // Sätter till null för att trigga felet

        when(repo.findById(1L)).thenReturn(Optional.of(session));

        // Act + Assert
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.chat(1L, "Hello")
        );

        assertEquals("Course has no vector store", exception.getMessage());

        // Verifierar att addMessage hinner köras
        verify(client).addMessage("thread-1", "Hello");

        // Men vi ska ALDRIG skapa en run eller spara sessionen efter detta fel
        verify(client, never()).createRun(anyString(), anyString());
        verify(repo, never()).save(any(AiSession.class));
    }

    @Test
    void shouldGetHistory() {
        // Arrange
        ChatHistoryMessage message = new ChatHistoryMessage("user", "Hej");

        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(client.getThreadMessages("thread-1")).thenReturn(List.of(message));

        // Act
        List<ChatHistoryMessage> result = service.getHistory(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("Hej", result.get(0).getContent());

        verify(repo).findById(1L);
        verify(client).getThreadMessages("thread-1");
    }

    @Test
    void shouldThrowWhenHistorySessionNotFound() {
        // Arrange
        when(repo.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getHistory(1L)
        );

        assertEquals("AI session not found", exception.getMessage());
        verifyNoInteractions(client);
    }
}