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
import se.liaprojekt.service.ai.VectorStoreService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private AiSessionRepository aiSessionRepository;

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
        course.setAiSessionTtlWeeks(6);

        section = new Section();
        section.setId(10L);
        section.setTitle("Intro");
        section.setOrderIndex(0);
        section.setCourse(course);

        // Använder en ArrayList så att listan är muterbar om koden skulle behöva modifiera den
        course.setSections(new ArrayList<>(List.of(section)));

        user = User.builder()
                .id(1L)
                .entraId("entra-123")
                .build();
    }

    @Test
    void shouldGetAllCourses() {
        when(courseRepository.findAll()).thenReturn(List.of(course));

        List<CourseResponse> result = courseService.getAllCourses();

        assertEquals(1, result.size());
        assertEquals("Java", result.getFirst().getTitle());
    }

    @Test
    void shouldGetAllRegisteredCourses() {
        // Arrange
        UserProgress userProgress = new UserProgress(user, course);
        userProgress.setCompletedSections(2);
        userProgress.setProgressPercentage(50);

        UserResponse userResponse = new UserResponse(1L, "Test", "Test", "User", "test@test.se", null);

        when(userProgressRepository.findByUserId(1L)).thenReturn(List.of(userProgress));
        when(userService.getUserResponseById(1L)).thenReturn(userResponse);

        // Act
        List<Map<String, Object>> result = courseService.getAllRegisteredCourses(1L);

        // Assert
        assertEquals(1, result.size());
        Map<String, Object> map = result.getFirst();

        assertTrue(map.containsKey("courseResponse"));
        assertTrue(map.containsKey("userProgressResponse"));

        CourseResponse cr = (CourseResponse) map.get("courseResponse");
        UserProgressResponse upr = (UserProgressResponse) map.get("userProgressResponse");

        assertEquals("Java", cr.getTitle());
        assertEquals(2, upr.completedSections());
        assertEquals(50, upr.progressPercentage());
    }

    @Test
    void shouldGetCourseById() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseResponse response = courseService.getCourseById(1L);

        assertEquals("Java", response.getTitle());
    }

    @Test
    void shouldThrowWhenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> courseService.getCourseById(99L)
        );

        assertEquals("Course not found with id: 99", exception.getMessage());
    }

    @Test
    void shouldCreateCourse() {
        // Arrange
        CourseRequest request = new CourseRequest("Spring", "Spring Boot course", 6);

        when(currentUserService.getName()).thenReturn("Teacher");
        when(vectorStoreService.createVectorStore("Spring")).thenReturn("vs-spring-123");

        // Mockar repository.save() att returnera det som skickas in
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CourseResponse response = courseService.createCourse(request);

        // Assert
        assertEquals("Spring", response.getTitle());
        assertEquals("Teacher", response.getCreatedBy());
        assertEquals(6, response.getAiSessionTtlWeeks());

        // Verifierar att det sparas två gånger (en gång initialt, en gång med vectorstore id)
        verify(courseRepository, times(2)).save(any(Course.class));
        verify(vectorStoreService).createVectorStore("Spring");
    }

    @Test
    void shouldUpdateCourse() {
        CourseRequest request = new CourseRequest("Updated", "Updated desc", 1);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponse response = courseService.updateCourse(1L, request);

        assertEquals("Updated", response.getTitle());
        assertEquals("Updated desc", response.getDescription());
        assertEquals(1, response.getAiSessionTtlWeeks());
    }

    @Test
    void shouldDeleteCourse() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.deleteCourse(1L);

        // Verifierar rätt ordningsföljd för borttagning
        verify(aiSessionRepository).deleteByCourseId(1L);
        verify(sectionService).deleteSection(10L);
        verify(courseRepository).delete(course);
    }

    @Test
    void shouldAddStudentsToCourse() {
        // Arrange
        UserRequest request = new UserRequest(1L);
        UserResponse userResponse = new UserResponse(1L, "Test", "Test", "User", "test@test.se", null);
        UserProgress progress = new UserProgress(user, course);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userService.getUserById(1L)).thenReturn(user);

        // Stubbar för det interna anropet till getStudentsInCourse(courseId)
        when(userProgressRepository.findByCourseId(1L)).thenReturn(List.of(progress));
        when(userService.getUserResponseById(1L)).thenReturn(userResponse);

        // Act
        List<UserProgressResponse> result = courseService.addStudentsToCourse(1L, List.of(request));

        // Assert
        assertEquals(1, result.size());
        verify(userProgressRepository).saveAll(anyList());
        verify(userProgressRepository).findByCourseId(1L);
    }

    @Test
    void shouldGetStudentsInCourse() {
        UserProgress progress = new UserProgress(user, course);
        UserResponse response = new UserResponse(1L, "Test", "Test", "User", "mail@test.se", null);

        when(userProgressRepository.findByCourseId(1L)).thenReturn(List.of(progress));
        when(userService.getUserResponseById(1L)).thenReturn(response);

        List<UserProgressResponse> result = courseService.getStudentsInCourse(1L);

        assertEquals(1, result.size());
        assertEquals("Test", result.getFirst().userResponse().displayName());
    }

    @Test
    void shouldReturnTrueWhenCourseCompleted() {
        TestResult result = new TestResult();
        result.setStatus(TestResult.Status.COMPLETED);

        when(sectionRepository.findByCourseId(1L)).thenReturn(List.of(section));
        when(testResultRepository.findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc("entra-123", 10L))
                .thenReturn(Optional.of(result));

        boolean completed = courseService.isCourseCompleted("entra-123", course);

        assertTrue(completed);
    }

    @Test
    void shouldReturnFalseWhenCourseNotCompleted() {
        when(sectionRepository.findByCourseId(1L)).thenReturn(List.of(section));
        when(testResultRepository.findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc("entra-123", 10L))
                .thenReturn(Optional.empty());

        boolean completed = courseService.isCourseCompleted("entra-123", course);

        assertFalse(completed);
    }

    @Test
    void shouldGetCourseProgress() {
        TestResult result = new TestResult();
        result.setStatus(TestResult.Status.COMPLETED);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(testResultRepository.findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc("entra-123", 10L))
                .thenReturn(Optional.of(result));

        CourseProgressResponse response = courseService.getCourseProgress(1L, "entra-123");

        assertEquals(1, response.totalSections());
        assertEquals(1, response.completedSections());
        assertEquals(100, response.progressPercentage());
    }

    // NYTT TEST: Säkerställer att kurser utan sektioner inte kastar division-med-noll fel
    @Test
    void shouldGetCourseProgressWithZeroSections() {
        // Arrange
        course.setSections(new ArrayList<>()); // Tom kurs
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        // Act
        CourseProgressResponse response = courseService.getCourseProgress(1L, "entra-123");

        // Assert
        assertEquals(0, response.totalSections());
        assertEquals(0, response.completedSections());
        assertEquals(0, response.progressPercentage());
    }

    @Test
    void shouldAssignAssistant() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.assignAssistant(1L, "assistant-123");

        assertEquals("assistant-123", course.getAssistantId());
        verify(courseRepository).save(course);
    }

    @Test
    void shouldGetCourseEntity() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        Course result = courseService.getCourseEntity(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void shouldGetCourseBySection() {
        when(sectionRepository.findById(10L)).thenReturn(Optional.of(section));

        Course result = courseService.getCourseBySection(10L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }
}