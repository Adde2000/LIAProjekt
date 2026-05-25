package se.liaprojekt.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import se.liaprojekt.dto.CourseRequest;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.dto.SectionRequest;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.Section;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.SectionRepository;
import se.liaprojekt.service.CurrentUserService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {

    @Autowired
    private CourseController controller;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @MockBean
    private CurrentUserService currentUserService;

    private static final int NUMBER_OF_COURSES = 10;
    private static List<Course> preloadedCourses = new ArrayList<>();
    private static final Map<Long, Course> courseMap = new HashMap<>();

    @BeforeAll
    static void setUpBeforeClass() {
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            preloadedCourses.add(Course.builder()
                    .title("Course " + i)
                    .description("CourseDescription" + i)
                    .createdBy("CourseCreator " + i)
                    .build()
            );
        }
    }

    @BeforeEach
    void setUp() {
        preloadedCourses = courseRepository.saveAll(preloadedCourses);
        for (Course course : preloadedCourses) {
            courseMap.put(course.getId(), course);
        }
    }

    @AfterEach
    void tearDown() {
        courseRepository.deleteAll();
    }


    @Test
    void getAllCoursesEmpty() {
        courseRepository.deleteAll();

        ResponseEntity<List<CourseResponse>> responseEntity = controller.getAllCourses();
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        List<CourseResponse> courseResponses = responseEntity.getBody();
        assertNotNull(courseResponses, "Course list is null");
        assertTrue(courseResponses.isEmpty(), "Course list is not empty");
    }

    @Test
    void getAllCourses() {
        ResponseEntity<List<CourseResponse>> responseEntity = controller.getAllCourses();
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        List<CourseResponse> courseResponses = responseEntity.getBody();
        assertNotNull(courseResponses, "Course list is null");
        assertEquals(NUMBER_OF_COURSES, courseResponses.size(), "Wrong number of courses");
        for (CourseResponse courseResponse : courseResponses) {
            Course course = courseMap.get(courseResponse.getId());
            checkCourseResponse(course, courseResponse);
        }
    }

    @Test
    void getCourseById() {
        ResponseEntity<CourseResponse> responseEntity = controller.getCourseById(preloadedCourses.getFirst().getId());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        CourseResponse courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course response is null");
        Course course = courseMap.get(courseResponse.getId());
        checkCourseResponse(course, courseResponse);
    }

    @Test
    void getCourseByIdNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> controller.getCourseById(-1L));
    }

    @Test
    void getCourseStudents() {
        assertTrue(false);
    }

    @Test
    void addStudentsToCourse() {
        assertTrue(false);
    }

    @Test
    void createCourse() {
        Mockito.when(currentUserService.getName()).thenReturn("Test");

        CourseRequest courseRequest = new CourseRequest(
                "TestTitle",
                "TestDescription"
        );
        ResponseEntity<CourseResponse> responseEntity = controller.createCourse(courseRequest);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode(), "Wrong status code");

        CourseResponse courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course response is null");
        Optional<Course> course = courseRepository.findById(courseResponse.getId());
        assertTrue(course.isPresent(), "Course not found");
        checkCourseResponse(course.get(), courseResponse);

        assertEquals(courseRequest.title(), courseResponse.getTitle(), "Wrong title");
        assertEquals(courseRequest.description(), courseResponse.getDescription(), "Wrong description");

        List<Course> courses = courseRepository.findAll();
        assertNotNull(courses, "Course list is null");
        assertEquals(NUMBER_OF_COURSES+1, courses.size(), "Wrong number of courses");
    }

    @Test
    void addSection() {
        long courseId = preloadedCourses.getFirst().getId();
        List<SectionRequest> sectionRequests = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            SectionRequest sectionRequest = new SectionRequest("TestSection" + i);
            sectionRequests.add(sectionRequest);
            controller.addSection(courseId, sectionRequest);
        }

        List<Section> sections = sectionRepository.findAll();
        assertNotNull(sections, "Section list is null");
        assertEquals(NUMBER_OF_COURSES, sections.size(), "Wrong number of courses");
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            Section section = sections.get(i);
            SectionRequest sectionRequest = sectionRequests.get(i);
            assertEquals(courseId, section.getCourse().getId(), "Wrong course id");
            assertEquals(sectionRequest.title(), section.getTitle(), "Wrong title");
        }

    }

    @Test
    void getSections() {
        assertTrue(false);
    }

    @Test
    void completeCourse() {
        assertTrue(false);
    }

    @Test
    void updateCourse() {
        long courseId = preloadedCourses.getFirst().getId();
        CourseRequest courseRequest = new CourseRequest(
                "NewTestTitle",
                "NewTestDescription"
        );
        ResponseEntity<CourseResponse> responseEntity = controller.updateCourse(courseId, courseRequest);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");
        CourseResponse courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course response is null");
        assertEquals(courseRequest.title(), courseResponse.getTitle(), "Wrong title in response body");
        assertEquals(courseRequest.description(), courseResponse.getDescription(), "Wrong description in response body");

        Optional<Course> updatedCourse = courseRepository.findById(courseId);
        assertTrue(updatedCourse.isPresent(), "Course not found");
        checkCourseResponse(updatedCourse.get(), courseResponse);
    }

    @Test
    void deleteCourse() {
        long courseId = preloadedCourses.getFirst().getId();
        ResponseEntity<Void> responseEntity = controller.deleteCourse(courseId);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode(), "Wrong status code");

        assertFalse(courseRepository.findById(courseId).isPresent(), "Course not deleted");
        assertEquals(NUMBER_OF_COURSES-1, courseRepository.findAll().size(), "Wrong number of courses");
    }

    @Test
    void getProgress() {
        assertTrue(false);
    }

    //Helpers

    private void checkCourseResponse(Course course, CourseResponse courseResponse){
        assertNotNull(courseResponse, "Course response is null");
        assertEquals(course.getId(), courseResponse.getId(), "Wrong course id");
        assertEquals(course.getTitle(), courseResponse.getTitle(), "Wrong course title");
        assertEquals(course.getDescription(), courseResponse.getDescription(), "Wrong course description");
    }
}