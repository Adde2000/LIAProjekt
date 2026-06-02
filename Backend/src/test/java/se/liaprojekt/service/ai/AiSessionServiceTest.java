package se.liaprojekt.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.model.AiSession;
import se.liaprojekt.model.Course;
import se.liaprojekt.repository.AiSessionRepository;
import se.liaprojekt.repository.CourseRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSessionServiceTest {

    @Mock
    private AiSessionRepository repo;

    @Mock
    private AzureAssistantClient client;

    @Mock
    private CourseRepository courseRepo;

    @InjectMocks
    private AiSessionService service;

    @Test
    void shouldCreateSession() {

        Course course = new Course();
        course.setId(1L);
        course.setVectorStoreId("vs-123");

        when(courseRepo.findById(1L))
                .thenReturn(Optional.of(course));

        when(client.createThread("vs-123"))
                .thenReturn("thread-123");

        AiSession savedSession = new AiSession();
        savedSession.setId(1L);
        savedSession.setThreadId("thread-123");

        when(repo.save(any(AiSession.class)))
                .thenReturn(savedSession);

        AiSession result = service.createSession(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("thread-123", result.getThreadId());

        verify(courseRepo).findById(1L);
        verify(client).createThread("vs-123");
        verify(repo).save(any(AiSession.class));
    }

    @Test
    void shouldGetSession() {

        // Arrange
        AiSession session = new AiSession();
        session.setId(1L);
        session.setThreadId("thread-abc");

        when(repo.findById(1L))
                .thenReturn(Optional.of(session));

        // Act
        AiSession result = service.get(1L);

        // Assert
        assertEquals(
                1L,
                result.getId()
        );

        assertEquals(
                "thread-abc",
                result.getThreadId()
        );
    }

    @Test
    void shouldThrowWhenSessionNotFound() {

        // Arrange
        when(repo.findById(1L))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                RuntimeException.class,
                () -> service.get(1L)
        );
    }
}