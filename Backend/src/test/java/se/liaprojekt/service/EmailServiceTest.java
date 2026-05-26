package se.liaprojekt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import se.liaprojekt.event.EmailEvent;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.EmailType;
import se.liaprojekt.producer.EmailEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailEventPublisher publisher;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(
                emailService,
                "emailEnabled",
                true
        );
    }

    @Test
    void shouldSendTestResultEmail() {

        // Act
        emailService.sendTestResultEmail(
                "test@test.com",
                100
        );

        // Assert
        ArgumentCaptor<EmailEvent> captor =
                ArgumentCaptor.forClass(EmailEvent.class);

        verify(publisher).publish(captor.capture());

        EmailEvent event = captor.getValue();

        assertEquals(
                "test@test.com",
                event.to()
        );

        assertEquals(
                "Test avklarat",
                event.subject()
        );

        assertTrue(
                event.body().contains("100%")
        );

        assertEquals(
                EmailType.TEST_RESULT,
                event.type()
        );
    }

    @Test
    void shouldSendCourseCompletedEmail() {

        // Arrange
        Course course = new Course();
        course.setTitle("Java Spring Boot");

        // Act
        emailService.sendCourseCompletedEmail(
                "student@test.com",
                course
        );

        // Assert
        ArgumentCaptor<EmailEvent> captor =
                ArgumentCaptor.forClass(EmailEvent.class);

        verify(publisher).publish(captor.capture());

        EmailEvent event = captor.getValue();

        assertEquals(
                "student@test.com",
                event.to()
        );

        assertEquals(
                "Kurs avklarad 🎉",
                event.subject()
        );

        assertTrue(
                event.body().contains("Java Spring Boot")
        );

        assertEquals(
                EmailType.COURSE_COMPLETED,
                event.type()
        );
    }

    @Test
    void shouldNotSendEmailWhenDisabled() {

        // Arrange
        ReflectionTestUtils.setField(
                emailService,
                "emailEnabled",
                false
        );

        // Act
        emailService.sendTestResultEmail(
                "test@test.com",
                80
        );

        // Assert
        verifyNoInteractions(publisher);
    }
}