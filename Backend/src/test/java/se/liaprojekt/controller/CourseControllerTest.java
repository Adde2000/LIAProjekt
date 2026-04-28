package se.liaprojekt.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import se.liaprojekt.dto.CourseRequest;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.dto.StudentRequest;
import se.liaprojekt.dto.StudentRespons;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseControllerTest {

    CourseController controller;
    private static CourseRequest courseRequest1;
    private static CourseRequest courseRequest2;
    private static CourseRequest courseRequest3;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        courseRequest1 = new CourseRequest(1L, "TestCourse1", "This is the first test course");
        courseRequest2 = new CourseRequest(2L, "TestCourse2", "This is the second test course");
        courseRequest3 = new CourseRequest(3L, "TestCourse3", "This is the third test course");
    }

    @BeforeEach
    void setUp() {
        controller = new CourseController();
    }

    @Test
    void getAllCoursesEmpty() {
        ResponseEntity<List<CourseResponse>> responseEntity = controller.getAllCourses();
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        List<CourseResponse> courses = responseEntity.getBody();
        assertNotNull(courses, "Course list is null");
        assertTrue(courses.isEmpty(), "Course list is not empty");
    }
    @Test
    void getAllCourses() {
        ResponseEntity<List<CourseResponse>> responseEntity = controller.getAllCourses();
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        controller.createCourse(courseRequest1);
        List<CourseResponse> courses = responseEntity.getBody();
        assertNotNull(courses, "Course list is null");
        assertEquals(1, courses.size(), "Course list is not the correct size");
        assertEquals(courseRequest1.title(), courses.getFirst().getTitle(), "Course title does not match");
        assertEquals(courseRequest1.description(), courses.getFirst().getDescription(), "Course description does not match");

        controller.createCourse(courseRequest2);
        controller.createCourse(courseRequest3);
        assertEquals(3, courses.size(), "Course list is not the correct size");

    }

    @Test
    void getCourseByIdNotFound() {
        ResponseEntity<CourseResponse> responseEntity = controller.getCourseById(4L);
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode(), "Wrong status code");
    }

    @Test
    void getCourseById() {
        CourseResponse courseResponse1 = controller.createCourse(courseRequest1).getBody();
        CourseResponse courseResponse2 = controller.createCourse(courseRequest2).getBody();
        CourseResponse courseResponse3 = controller.createCourse(courseRequest3).getBody();

        assertNotNull(courseResponse1, "Course is null from createCourse");
        assertNotNull(courseResponse2, "Course is null from createCourse");
        assertNotNull(courseResponse3, "Course is null from createCourse");

        ResponseEntity<CourseResponse> responseEntity = controller.getCourseById(courseResponse1.getId());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");
        CourseResponse courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course is null from getCourseById");
        assertEquals(courseRequest1.title(), courseResponse.getTitle(), "Course title does not match");
        assertEquals(courseRequest1.description(), courseResponse.getDescription(), "Course description does not match");

        responseEntity = controller.getCourseById(courseResponse2.getId());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");
        courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course is null from getCourseById");
        assertEquals(courseRequest2.title(), courseResponse.getTitle(), "Course title does not match");
        assertEquals(courseRequest2.description(), courseResponse.getDescription(), "Course description does not match");

        responseEntity = controller.getCourseById(courseResponse3.getId());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");
        courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course is null from getCourseById");
        assertEquals(courseRequest3.title(), courseResponse.getTitle(), "Course title does not match");
        assertEquals(courseRequest3.description(), courseResponse.getDescription(), "Course description does not match");
    }

    @Test
    void getCourseStudents() {

    }

    @Test
    void addCourseStudent() {
        CourseResponse courseResponse = controller.createCourse(courseRequest1).getBody();
        assertNotNull(courseResponse, "Course is null from createCourse");

        List<StudentRequest> students = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            students.add(new StudentRequest(i+1L));
        }

        ResponseEntity<String> responseEntity = controller.addCourseStudent(courseResponse.getId(), students);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        ResponseEntity<List<StudentRespons>> studentResponsResponseEntity = controller.getCourseStudents(courseResponse.getId());
        assertEquals(HttpStatus.OK, studentResponsResponseEntity.getStatusCode(), "Wrong status code");
        List<StudentRespons> studentResponsList = studentResponsResponseEntity.getBody();
        assertNotNull(studentResponsList, "Course is null from getCourseStudents");
        assertEquals(10, studentResponsList.size(), "Course list is not the correct size");
        for (int i = 0; i < 10; i++) {
            assertEquals(students.get(i).studentId(), studentResponsList.get(i).id(), "User id does not match");
        }
    }

    @Test
    void createCourse() {
        ResponseEntity<List<CourseResponse>> responseEntity = controller.getAllCourses();
        List<CourseResponse> courses = responseEntity.getBody();
        assertNotNull(courses, "Course list is null");
        assertTrue(courses.isEmpty(), "Course list before creation is not empty");

        ResponseEntity<CourseResponse> createCourseResponseEntity = controller.createCourse(courseRequest1);
        assertEquals(HttpStatus.CREATED, createCourseResponseEntity.getStatusCode(), "Wrong status code");

        CourseResponse courseResponse = createCourseResponseEntity.getBody();
        assertNotNull(courseResponse, "Course is null from getCourseById");
        assertEquals(courseRequest1.title(), courseResponse.getTitle(), "Course title does not match");
        assertEquals(courseRequest1.description(), courseResponse.getDescription(), "Course description does not match");

        List<CourseResponse> courseResponseList = controller.getAllCourses().getBody();
        assertNotNull(courseResponseList, "Course list is null from getAllCourses");
        assertFalse(courseResponseList.isEmpty(), "Course was not created");
        assertEquals(1, courseResponseList.size(), "Course list is not the correct size");
    }

    @Test
    void addSection() {
    }

    @Test
    void completeCourse() {
    }
}