package se.liaprojekt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import se.liaprojekt.dto.TestAnswerRequest;
import se.liaprojekt.dto.TestQuestionRequest;
import se.liaprojekt.dto.TestResultResponse;
import se.liaprojekt.event.TestResultEvent;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestServiceTest {

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private TestQuestionRepository questionRepository;

    @Mock
    private AnsweredQuestionRepository answeredQuestionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProgressRepository userProgressRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TestService testService;

    private User user;
    private Course course;
    private Section section;

    @BeforeEach
    void setUp() {

        user = new User();
        user.setId(1L);
        user.setEntraId("entra-123");

        course = new Course();
        course.setId(10L);

        section = new Section();
        section.setId(100L);
        section.setCourse(course);
        section.setOrderIndex(0);
    }

    @Test
    void shouldCreateQuestion() {

        // Arrange
        TestAnswerRequest a1 =
                new TestAnswerRequest(
                        "Java",
                        true
                );

        TestAnswerRequest a2 =
                new TestAnswerRequest(
                        "Python",
                        false
                );

        TestQuestionRequest request =
                new TestQuestionRequest(
                        "Best language?",
                        List.of(a1, a2)
                );

        when(sectionRepository.findById(100L))
                .thenReturn(Optional.of(section));

        when(questionRepository.save(any(TestQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TestQuestion result =
                testService.createQuestion(100L, request);

        // Assert
        assertEquals(
                "Best language?",
                result.getQuestionText()
        );

        assertEquals(
                2,
                result.getAnswers().size()
        );

        verify(questionRepository).save(any(TestQuestion.class));
    }

    @Test
    void shouldThrowWhenQuestionHasMultipleCorrectAnswers() {

        // Arrange
        TestAnswerRequest a1 =
                new TestAnswerRequest(
                        "A",
                        true
                );

        TestAnswerRequest a2 =
                new TestAnswerRequest(
                        "B",
                        true
                );

        TestQuestionRequest request =
                new TestQuestionRequest(
                        "Question",
                        List.of(a1, a2)
                );

        when(sectionRepository.findById(1L))
                .thenReturn(Optional.of(section));

        // Assert
        assertThrows(
                BadRequestException.class,
                () -> testService.createQuestion(1L, request)
        );
    }

    @Test
    void shouldStartTest() {

        // Arrange
        when(sectionRepository.findById(100L))
                .thenReturn(Optional.of(section));

        when(userRepository.findByEntraId("entra-123"))
                .thenReturn(Optional.of(user));

        when(testResultRepository
                .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        100L
                ))
                .thenReturn(List.of());

        when(testResultRepository.save(any(TestResult.class)))
                .thenAnswer(invocation -> {

                    TestResult result = invocation.getArgument(0);
                    result.setId(1L);
                    return result;
                });

        // Act
        TestResultResponse response =
                testService.startTest(
                        "entra-123",
                        100L
                );

        // Assert
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(1, response.attemptNumber());
    }

    @Test
    void shouldSubmitAnswer() {

        // Arrange
        when(currentUserService.getEntraId())
                .thenReturn("entra-123");

        TestResult result = new TestResult();
        result.setId(1L);
        result.setUser(user);
        result.setStatus(TestResult.Status.IN_PROGRESS);

        TestAnswer answer = new TestAnswer();
        answer.setId(5L);
        answer.setIsCorrect(true);

        TestQuestion question = new TestQuestion();
        question.setId(10L);
        question.setAnswers(List.of(answer));

        when(testResultRepository.findById(1L))
                .thenReturn(Optional.of(result));

        when(questionRepository.findByIdWithAnswers(10L))
                .thenReturn(Optional.of(question));

        when(answeredQuestionRepository
                .findByTestResult_IdAndQuestion_Id(1L, 10L))
                .thenReturn(Optional.empty());

        // Act
        testService.submitAnswer(
                1L,
                10L,
                5L
        );

        // Assert
        verify(answeredQuestionRepository)
                .save(any(AnsweredQuestion.class));
    }

    @Test
    void shouldNotAllowSubmitAnswerFromAnotherUser() {

        // Arrange
        when(currentUserService.getEntraId())
                .thenReturn("wrong-user");

        TestResult result = new TestResult();
        result.setUser(user);

        when(testResultRepository.findById(1L))
                .thenReturn(Optional.of(result));

        // Assert
        assertThrows(
                BadRequestException.class,
                () -> testService.submitAnswer(
                        1L,
                        1L,
                        1L
                )
        );
    }

    @Test
    void shouldSubmitTestAndPass() {

        // Arrange
        when(currentUserService.getEntraId())
                .thenReturn("entra-123");

        when(userProgressRepository.findByCourseIdAndUserId(course.getId(), user.getId()))
                .thenReturn(UserProgress.builder().user(user).build());

        when(userProgressRepository.save(any(UserProgress.class))).thenReturn(null);

        TestResult result = new TestResult();
        result.setId(1L);
        result.setUser(user);
        result.setSection(section);
        result.setStatus(TestResult.Status.IN_PROGRESS);

        AnsweredQuestion q1 = new AnsweredQuestion();
        q1.setCorrect(true);

        when(testResultRepository.findById(1L))
                .thenReturn(Optional.of(result));

        when(answeredQuestionRepository
                .findByTestResult_Id(1L))
                .thenReturn(List.of(q1));

        when(testResultRepository.save(any(TestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TestResultResponse response =
                testService.submitTest(1L);

        // Assert
        assertTrue(response.passed());
        assertEquals(100, response.score());
        assertEquals("COMPLETED", response.status());

        verify(eventPublisher)
                .publishEvent(any(TestResultEvent.class));
    }

    @Test
    void shouldSubmitTestAndFail() {

        // Arrange
        when(currentUserService.getEntraId())
                .thenReturn("entra-123");

        TestResult result = new TestResult();
        result.setId(1L);
        result.setUser(user);
        result.setStatus(TestResult.Status.IN_PROGRESS);

        AnsweredQuestion q1 = new AnsweredQuestion();
        q1.setCorrect(false);

        when(testResultRepository.findById(1L))
                .thenReturn(Optional.of(result));

        when(answeredQuestionRepository
                .findByTestResult_Id(1L))
                .thenReturn(List.of(q1));

        when(testResultRepository.save(any(TestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TestResultResponse response =
                testService.submitTest(1L);

        // Assert
        assertFalse(response.passed());
        assertEquals(0, response.score());
        assertEquals("FAILED", response.status());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void shouldReturnQuestions() {

        // Arrange
        TestAnswer answer = new TestAnswer();
        answer.setId(1L);
        answer.setAnswerText("Java");

        TestQuestion question = new TestQuestion();
        question.setId(1L);
        question.setQuestionText("Best language?");
        question.setAnswers(List.of(answer));

        when(questionRepository.findBySectionId(100L))
                .thenReturn(List.of(question));

        // Act
        var result = testService.getQuestions(100L);

        // Assert
        assertEquals(1, result.size());
        assertEquals(
                "Best language?",
                result.getFirst().questionText()
        );
    }

    @Test
    void shouldReturnAttempts() {

        // Arrange
        TestResult result = new TestResult();
        result.setId(1L);
        result.setStatus(TestResult.Status.COMPLETED);
        result.setScore(100);
        result.setPassed(true);
        result.setAttemptNumber(1);

        when(userRepository.findByEntraId("entra-123"))
                .thenReturn(Optional.of(user));

        when(testResultRepository
                .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        100L
                ))
                .thenReturn(List.of(result));

        // Act
        List<TestResultResponse> responses =
                testService.getAttempts(
                        "entra-123",
                        100L
                );

        // Assert
        assertEquals(1, responses.size());
        assertEquals(
                "COMPLETED",
                responses.getFirst().status()
        );
    }

    @Test
    void shouldThrowWhenSectionNotFound() {

        when(sectionRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> testService.startTest(
                        "entra-123",
                        999L
                )
        );
    }
}