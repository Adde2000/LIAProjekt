package se.liaprojekt.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import se.liaprojekt.dto.CourseRequest;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.repository.CourseRepository;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {

    @Autowired
    private CourseController controller;

    @Autowired
    private CourseRepository repository;

    private static final int NUMBER_OF_COURSES = 10;
    private static List<Course> preloadedCourses = new ArrayList<>();
    private static Map<Long, Course> courseMap = new HashMap<>();
    @Autowired
    private CourseRepository courseRepository;

//    private List<CourseRequest> courseRequests;

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
        preloadedCourses = repository.saveAll(preloadedCourses);
        for (Course course : preloadedCourses) {
            courseMap.put(course.getId(), course);
        }
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }


    @Test
    void getAllCoursesEmpty() {
        repository.deleteAll();

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
        assertTrue(false);
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
        assertTrue(false);
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