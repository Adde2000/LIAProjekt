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

    @BeforeEach
    void setUp() {

        Course course = new Course();
        course.setAssistantId("assistant-123");

        session = new AiSession();
        session.setId(1L);
        session.setThreadId("thread-1");
        session.setCourse(course);
        session.setLastUsedAt(LocalDateTime.now());
    }

    @Test
    void shouldChatSuccessfully() {

        // Arrange
        when(repo.findById(1L))
                .thenReturn(Optional.of(session));

        when(client.createRun(
                "thread-1",
                "assistant-123"
        )).thenReturn("run-1");

        when(client.waitForCompletion(
                "thread-1",
                "run-1"
        )).thenReturn("AI response");

        // Act
        String result = service.chat(
                1L,
                "Hello AI"
        );

        // Assert
        assertEquals("AI response", result);

        verify(client).addMessage(
                "thread-1",
                "Hello AI"
        );

        verify(client).createRun(
                "thread-1",
                "assistant-123"
        );

        verify(repo).save(session);

        verify(client).waitForCompletion(
                "thread-1",
                "run-1"
        );
    }

    @Test
    void shouldThrowWhenMessageIsNull() {

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.chat(1L, null)
                );

        assertEquals(
                "Message cannot be empty",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowWhenMessageIsBlank() {

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.chat(1L, " ")
                );

        assertEquals(
                "Message cannot be empty",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowWhenMessageTooLong() {

        String longMessage = "a".repeat(5001);

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.chat(1L, longMessage)
                );

        assertTrue(
                exception.getMessage()
                        .contains("Message exceeds max length")
        );
    }

    @Test
    void shouldThrowWhenSessionNotFound() {

        when(repo.findById(1L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> service.chat(1L, "Hello")
                );

        assertEquals(
                "AI session not found",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowWhenAssistantIdMissing() {

        // Arrange
        Course course = new Course();
        course.setAssistantId(null);

        session.setCourse(course);

        when(repo.findById(1L))
                .thenReturn(Optional.of(session));

        // Act + Assert
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.chat(1L, "Hello")
                );

        assertEquals(
                "No assistant assigned to course",
                exception.getMessage()
        );
    }

    @Test
    void shouldGetHistory() {

        // Arrange
        ChatHistoryMessage message =
                new ChatHistoryMessage(
                        "user",
                        "Hej"
                );

        when(repo.findById(1L))
                .thenReturn(Optional.of(session));

        when(client.getThreadMessages("thread-1"))
                .thenReturn(List.of(message));

        // Act
        List<ChatHistoryMessage> result =
                service.getHistory(1L);

        // Assert
        assertEquals(1, result.size());

        assertEquals(
                "user",
                result.get(0).getRole()
        );

        assertEquals(
                "Hej",
                result.get(0).getContent()
        );
    }

    @Test
    void shouldThrowWhenHistorySessionNotFound() {

        when(repo.findById(1L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> service.getHistory(1L)
                );

        assertEquals(
                "AI session not found",
                exception.getMessage()
        );
    }
}