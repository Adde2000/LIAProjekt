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

        when(userRepo.findById(1L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> service.createSession(1L, 10L)
                );

        assertEquals(
                "User not found: 1",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowWhenCourseNotFound() {

        when(userRepo.findById(1L))
                .thenReturn(Optional.of(user));

        when(courseRepo.findById(10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> service.createSession(1L, 10L)
                );

        assertEquals(
                "Course not found: 10",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowWhenAssistantMissing() {

        // Arrange
        course.setAssistantId(null);

        when(userRepo.findById(1L))
                .thenReturn(Optional.of(user));

        when(courseRepo.findById(10L))
                .thenReturn(Optional.of(course));

        // Act + Assert
        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.createSession(1L, 10L)
                );

        assertEquals(
                "Course does not have an AI assistant assigned",
                exception.getMessage()
        );
    }

    @Test
    void shouldReuseExistingSession() {

        // Arrange
        AiSession existing = new AiSession();
        existing.setId(100L);

        when(userRepo.findById(1L))
                .thenReturn(Optional.of(user));

        when(courseRepo.findById(10L))
                .thenReturn(Optional.of(course));

        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L))
                .thenReturn(List.of(existing));

        // Act
        AiSession result =
                service.createSession(1L, 10L);

        // Assert
        assertEquals(100L, result.getId());

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

        when(userRepo.findById(1L))
                .thenReturn(Optional.of(user));

        when(courseRepo.findById(10L))
                .thenReturn(Optional.of(course));

        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L))
                .thenReturn(List.of(keep, duplicate));

        // Act
        AiSession result =
                service.createSession(1L, 10L);

        // Assert
        assertEquals(1L, result.getId());

        verify(sessionRepo)
                .deleteAll(List.of(duplicate));

        verify(client, never()).createThread(anyString());
    }

    @Test
    void shouldCreateNewSession() {

        // Arrange
        when(userRepo.findById(1L))
                .thenReturn(Optional.of(user));

        when(courseRepo.findById(10L))
                .thenReturn(Optional.of(course));

        when(sessionRepo.findAllByUser_IdAndCourse_Id(1L, 10L))
                .thenReturn(List.of());

        when(client.createThread(anyString()))
                .thenReturn("thread-123");

        AiSession savedSession = new AiSession();
        savedSession.setThreadId("thread-123");

        when(sessionRepo.save(any(AiSession.class)))
                .thenReturn(savedSession);

        // Act
        AiSession result =
                service.createSession(1L, 10L);

        // Assert
        assertEquals(
                "thread-123",
                result.getThreadId()
        );

        verify(client).createThread(anyString());

        verify(sessionRepo)
                .save(any(AiSession.class));
    }
}