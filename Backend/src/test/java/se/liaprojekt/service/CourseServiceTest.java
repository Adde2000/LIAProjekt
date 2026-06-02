package se.liaprojekt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.liaprojekt.dto.*;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private UserProgressRepository userProgressRepository;

    @Mock
    private UserService userService;

    @Mock
    private SectionService sectionService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private CourseService courseService;

    private Course course;
    private Section section;
    private User user;

    @BeforeEach
    void setUp() {

        course = new Course();
        course.setId(1L);
        course.setTitle("Java");
        course.setDescription("Java course");
        course.setCreatedBy("Admin");

        section = new Section();
        section.setId(10L);
        section.setTitle("Intro");
        section.setOrderIndex(0);
        section.setCourse(course);

        course.setSections(List.of(section));

        user = User.builder()
                .id(1L)
                .entraId("entra-123")
                .build();
    }

    @Test
    void shouldGetAllCourses() {

        when(courseRepository.findAll())
                .thenReturn(List.of(course));

        List<CourseResponse> result =
                courseService.getAllCourses();

        assertEquals(1, result.size());
        assertEquals("Java", result.getFirst().getTitle());
    }

    @Test
    void shouldGetCourseById() {

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        CourseResponse response =
                courseService.getCourseById(1L);

        assertEquals("Java", response.getTitle());
    }

    @Test
    void shouldThrowWhenCourseNotFound() {

        when(courseRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> courseService.getCourseById(99L)
        );
    }

    @Test
    void shouldCreateCourse() {

        CourseRequest request =
                new CourseRequest(
                        "Spring",
                        "Spring Boot course",
                        6,
                        null
                );

        when(currentUserService.getName())
                .thenReturn("Teacher");

        when(courseRepository.save(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponse response =
                courseService.createCourse(request);

        assertEquals("Spring", response.getTitle());
        assertEquals("Teacher", response.getCreatedBy());
        assertEquals(6, response.getAiSessionTtlWeeks());
    }

    @Test
    void shouldUpdateCourse() {

        CourseRequest request =
                new CourseRequest(
                        "Updated",
                        "Updated desc",
                        1,
                        null
                );

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        when(courseRepository.save(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponse response =
                courseService.updateCourse(1L, request);

        assertEquals("Updated", response.getTitle());
        assertEquals("Updated desc", response.getDescription());
        assertEquals(1, response.getAiSessionTtlWeeks());
    }

    @Test
    void shouldDeleteCourse() {

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        courseService.deleteCourse(1L);

        verify(sectionService)
                .deleteSection(10L);

        verify(courseRepository)
                .delete(course);
    }

    @Test
    void shouldAddStudentsToCourse() {

        Course course = new Course();
        course.setId(1L);

        UserRequest request =
                new UserRequest(1L);

        UserResponse userResponse =
                new UserResponse(
                        1L,
                        "Test",
                        "Test",
                        "User",
                        "test@test.se",
                        null
                );

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        when(userService.getUserById(1L))
                .thenReturn(user);

        when(userProgressRepository.findByCourseId(1L))
                .thenReturn(List.of(
                        new UserProgress(user, course)
                ));

        when(userService.getUserResponseById(1L))
                .thenReturn(userResponse);

        List<UserProgressResponse> result =
                courseService.addStudentsToCourse(
                        1L,
                        List.of(request)
                );

        assertEquals(1, result.size());

        verify(userProgressRepository)
                .saveAll(anyList());
    }

    @Test
    void shouldGetStudentsInCourse() {

        UserProgress progress =
                new UserProgress(user, course);

        UserResponse response =
                new UserResponse(
                        1L,
                        "Test",
                        "Test",
                        "User",
                        "mail@test.se",
                        null
                );

        when(userProgressRepository.findByCourseId(1L))
                .thenReturn(List.of(progress));

        when(userService.getUserResponseById(1L))
                .thenReturn(response);

        List<UserProgressResponse> result =
                courseService.getStudentsInCourse(1L);

        assertEquals(1, result.size());
        assertEquals("Test", result.getFirst().userResponse().displayName());
    }

    @Test
    void shouldReturnTrueWhenCourseCompleted() {

        TestResult result = new TestResult();
        result.setStatus(TestResult.Status.COMPLETED);

        when(sectionRepository.findByCourseId(1L))
                .thenReturn(List.of(section));

        when(testResultRepository
                .findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        10L
                ))
                .thenReturn(Optional.of(result));

        boolean completed =
                courseService.isCourseCompleted(
                        "entra-123",
                        course
                );

        assertTrue(completed);
    }

    @Test
    void shouldReturnFalseWhenCourseNotCompleted() {

        when(sectionRepository.findByCourseId(1L))
                .thenReturn(List.of(section));

        when(testResultRepository
                .findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        10L
                ))
                .thenReturn(Optional.empty());

        boolean completed =
                courseService.isCourseCompleted(
                        "entra-123",
                        course
                );

        assertFalse(completed);
    }

    @Test
    void shouldGetCourseProgress() {

        TestResult result = new TestResult();
        result.setStatus(TestResult.Status.COMPLETED);

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        when(testResultRepository
                .findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        10L
                ))
                .thenReturn(Optional.of(result));

        CourseProgressResponse response =
                courseService.getCourseProgress(
                        1L,
                        "entra-123"
                );

        assertEquals(1, response.totalSections());
        assertEquals(1, response.completedSections());
        assertEquals(100, response.progressPercentage());
    }

    @Test
    void shouldAssignAssistant() {

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        courseService.assignAssistant(
                1L,
                "assistant-123"
        );

        assertEquals(
                "assistant-123",
                course.getAssistantId()
        );

        verify(courseRepository)
                .save(course);
    }
}