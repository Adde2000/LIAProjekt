package se.liaprojekt.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.AiSessionRepository;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSessionInitServiceTest {

    @Mock
    private AzureAssistantClient client;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private AiSessionRepository sessionRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private CourseRepository courseRepo;

    @InjectMocks
    private AiSessionInitService service;

    private User user;
    private Course course;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        course = new Course();
        course.setId(10L);
        course.setAssistantId("assistant-123");
        course.setVectorStoreId("vs-123");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.createSession(1L, 10L)
        );

        assertEquals("User not found: 1", exception.getMessage());
        verifyNoInteractions(courseRepo, vectorStoreService, sessionRepo, client);
    }

    @Test
    void shouldThrowWhenCourseNotFound() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(courseRepo.findById(10L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.createSession(1L, 10L)
        );

        assertEquals("Course not found: 10", exception.getMessage());
        verifyNoInteractions(vectorStoreService, sessionRepo, client);
    }

    @Test
    void shouldThrowWhenAssistantMissing() {
        // Arrange
        course.setAssistantId(null); // Eller "" / " "
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));

        // Act + Assert
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.createSession(1L, 10L)
        );

        assertEquals("Course does not have an AI assistant assigned", exception.getMessage());
        verify(vectorStoreService).initializeCourseVectorStore(course); // Körs innan valideringen
        verifyNoInteractions(sessionRepo, client);
    }

    @Test
    void shouldReuseExistingSession() {
        // Arrange
        AiSession existing = new AiSession();
        existing.setId(100L);
        existing.setUser(user);
        existing.setCourse(course);

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));
        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L)).thenReturn(List.of(existing));

        // Act
        AiSession result = service.createSession(1L, 10L);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getId());

        verify(vectorStoreService).initializeCourseVectorStore(course);
        verify(client, never()).createThread(anyString());
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void shouldCleanupDuplicateSessions() {
        // Arrange
        AiSession keep = new AiSession();
        keep.setId(1L);

        AiSession duplicate = new AiSession();
        duplicate.setId(2L);

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));
        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L)).thenReturn(List.of(keep, duplicate));

        // Act
        AiSession result = service.createSession(1L, 10L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());

        verify(vectorStoreService).initializeCourseVectorStore(course);
        verify(sessionRepo).deleteAll(List.of(duplicate));
        verify(client, never()).createThread(anyString());
    }

    @Test
    void shouldCreateNewSession() {
        // Arrange
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));
        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L)).thenReturn(List.of());

        // Verifierar att rätt vectorStoreId ("vs-123") skickas till klienten
        when(client.createThread("vs-123")).thenReturn("thread-123");

        // Bygger upp den förväntade sparade sessionen korrekt
        AiSession savedSession = new AiSession();
        savedSession.setId(555L);
        savedSession.setThreadId("thread-123");
        savedSession.setUser(user);
        savedSession.setCourse(course);

        when(sessionRepo.save(any(AiSession.class))).thenReturn(savedSession);

        // Act
        AiSession result = service.createSession(1L, 10L);

        // Assert
        assertNotNull(result);
        assertEquals(555L, result.getId());
        assertEquals("thread-123", result.getThreadId());

        verify(vectorStoreService).initializeCourseVectorStore(course);
        verify(client).createThread("vs-123");
        verify(sessionRepo).save(any(AiSession.class));
    }

    @Test
    void shouldThrowWhenVectorStoreMissingOnNewSession() {
        // Arrange
        course.setVectorStoreId(null); // Tar bort vector store id så att det blir null/blankt

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(courseRepo.findById(10L)).thenReturn(Optional.of(course));

        // Koden kommer att köra den här raden, så vi mockar den till en tom lista
        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L)).thenReturn(List.of());

        // Act + Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createSession(1L, 10L)
        );

        assertEquals("Course has no vector store configured", exception.getMessage());

        // VERIFIERINGAR
        verify(vectorStoreService).initializeCourseVectorStore(course);
        verify(sessionRepo).findAllByUser_IdAndCourse_Id(1L, 10L); // Detta anrop sker!


        verifyNoInteractions(client);
        verify(sessionRepo, never()).save(any(AiSession.class));
    }
}