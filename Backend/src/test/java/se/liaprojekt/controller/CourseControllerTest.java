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
import org.springframework.security.test.context.support.WithMockUser;
import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.dto.*;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.Section;
import se.liaprojekt.model.User;
import se.liaprojekt.model.UserProgress;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.SectionRepository;
import se.liaprojekt.repository.UserProgressRepository;
import se.liaprojekt.repository.UserRepository;
import se.liaprojekt.service.BlobStorageService;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.GraphService;

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private BlobStorageService blobStorageService;

    @MockBean
    private GraphService graphService;

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
                    .aiSessionTtlWeeks(6)
                    .build()
            );
        }
    }

    @BeforeEach
    void setUp() {
        courseRepository.deleteAll();
        sectionRepository.deleteAll();
        userRepository.deleteAll();
        userProgressRepository.deleteAll();
        preloadedCourses = courseRepository.saveAll(preloadedCourses);
        for (Course course : preloadedCourses) {
            courseMap.put(course.getId(), course);
        }
    }

    @AfterEach
    void tearDown() {
        courseRepository.deleteAll();
        sectionRepository.deleteAll();
        userRepository.deleteAll();
        userProgressRepository.deleteAll();
    }


    @Test
    @WithMockUser(roles = "Admin")
    void getAllCoursesEmpty() {
        Mockito.when(currentUserService.getRoles()).thenReturn(Set.of(Roles.ADMIN));

        courseRepository.deleteAll();

        ResponseEntity<List<CourseResponse>> responseEntity = controller.getAllCourses();
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        List<CourseResponse> courseResponses = responseEntity.getBody();
        assertNotNull(courseResponses, "Course list is null");
        assertTrue(courseResponses.isEmpty(), "Course list is not empty");
    }

    @Test
    @WithMockUser(roles = "Admin")
    void getAllCourses() {
        Mockito.when(currentUserService.getRoles()).thenReturn(Set.of(Roles.ADMIN));

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
    @WithMockUser(roles = "Admin")
    void getCourseById() {
        ResponseEntity<CourseResponse> responseEntity = controller.getCourseById(preloadedCourses.getFirst().getId());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        CourseResponse courseResponse = responseEntity.getBody();
        assertNotNull(courseResponse, "Course response is null");
        Course course = courseMap.get(courseResponse.getId());
        checkCourseResponse(course, courseResponse);
    }

    @Test
    @WithMockUser(roles = "Admin")
    void getCourseByIdNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> controller.getCourseById(-1L));
    }

    @Test
    @WithMockUser(roles = "Admin")
    void getCourseStudents() {
        Course course = preloadedCourses.getFirst();
        Long courseId = course.getId();
        List<User> userList = loadUsers();

        List<UserProgress> userProgressList = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            User user = userList.get(i);
            UserProgress userProgress = UserProgress.builder()
                    .completedSections(i)
                    .progressPercentage(Math.divideExact(i*100, NUMBER_OF_COURSES))
                    .course(course)
                    .user(user)
                    .build();
            userProgressList.add(userProgress);
        }
        userProgressList = userProgressRepository.saveAll(userProgressList);

        ResponseEntity<List<UserProgressResponse>> responseEntity = controller.getCourseStudents(courseId);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");
        List<UserProgressResponse> userProgressResponses = responseEntity.getBody();
        assertNotNull(userProgressResponses, "Users list is null");
        assertEquals(NUMBER_OF_COURSES, userProgressResponses.size(), "Wrong number of users");
        Set<Long> userResponseIds = new HashSet<>();
        for (UserProgressResponse userProgressResponse : userProgressResponses) {
            userResponseIds.add(userProgressResponse.userResponse().id());
        }
        for (UserProgress userProgress : userProgressList) {
            assertTrue(userResponseIds.contains(userProgress.getUser().getId()));
        }
    }

    @Test
    @WithMockUser(roles = "Admin")
    void addStudentsToCourse() {
        Course course = preloadedCourses.getFirst();
        Long courseId = course.getId();
        List<User> userList = loadUsers();

        List<UserRequest> userRequestList = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            userRequestList.add(new UserRequest(userList.get(i).getId()));
        }

        ResponseEntity<List<UserProgressResponse>> responseEntity = controller.addStudentsToCourse(courseId, userRequestList);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");
        List<UserProgressResponse> userProgressResponses = responseEntity.getBody();
        assertNotNull(userProgressResponses, "Users list is null");
        assertEquals(NUMBER_OF_COURSES, userProgressResponses.size(), "Wrong number of users");

        //Update all lists after call to controller
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        assertTrue(courseOptional.isPresent(), "Course not found");
        course = courseOptional.get();
        userList = userRepository.findAll();
        List<UserProgress> userProgressList = course.getUserProgress();

        assertNotNull(userProgressList, "UserProgress is null");
        assertEquals(NUMBER_OF_COURSES, userProgressList.size(), "Wrong number of users in course");
        Set<Long> userProgressSet = new HashSet<>();
        for (UserProgress userProgress : userProgressList) {
            assertNotNull(userProgress.getUser(), "User not found");
            assertEquals(courseId, userProgress.getCourse().getId());
            userProgressSet.add(userProgress.getId());
        }

        for (User user : userList) {
            assertNotNull(user, "User not found");
            assertEquals(1, user.getUserProgressList().size(), "Wrong number of courses for user");
            assertTrue(userProgressSet.contains(user.getUserProgressList().getFirst().getId()), "Course does not have user");
            assertEquals(user.getId(), user.getUserProgressList().getFirst().getUser().getId(), "User progress does not have correct user");
        }
    }

    @Test
    @WithMockUser(roles = "Admin")
    void createCourse() {
        Mockito.when(currentUserService.getName()).thenReturn("Test");

        CourseRequest courseRequest = new CourseRequest(
                "TestTitle",
                "TestDescription",
                6,
                null
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
        assertEquals(6, course.get().getAiSessionTtlWeeks(), "Wrong TTL weeks"
        );

        List<Course> courses = courseRepository.findAll();
        assertNotNull(courses, "Course list is null");
        assertEquals(NUMBER_OF_COURSES+1, courses.size(), "Wrong number of courses");
    }

    @Test
    @WithMockUser(roles = "Admin")
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
        assertEquals(NUMBER_OF_COURSES, sections.size(), "Wrong number of sections");
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            Section section = sections.get(i);
            SectionRequest sectionRequest = sectionRequests.get(i);
            assertEquals(courseId, section.getCourse().getId(), "Wrong course id");
            assertEquals(sectionRequest.title(), section.getTitle(), "Wrong title");
            assertEquals(i, section.getOrderIndex(), "Wrong order index");
        }

    }

    @Test
    void getSections() {
        Course course = preloadedCourses.getFirst();
        List<Section> sections = loadSections(course);

        ResponseEntity<List<SectionResponse>> responseEntity = controller.getSections(course.getId());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Wrong status code");

        List<SectionResponse> sectionResponses = responseEntity.getBody();
        assertNotNull(sectionResponses, "Section list is null");
        assertEquals(NUMBER_OF_COURSES, sectionResponses.size(), "Wrong number of sections");
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            Section section = sections.get(i);
            SectionResponse sectionResponse = sectionResponses.get(i);
            assertEquals(section.getId(), sectionResponse.id(), "Wrong id for section " + i);
            assertEquals(section.getTitle(), sectionResponse.title(), "Wrong title");
            assertEquals(i, sectionResponse.orderIndex(), "Wrong order index");
            assertEquals(course.getId(), sectionResponse.courseId(), "Wrong course id");
            if (i == 0) {
                assertFalse(sectionResponse.isLocked(), "First section is locked");
            } else {
                assertTrue(sectionResponse.isLocked(), "Section is not locked");
            }
        }
    }

    @Test
    void completeCourse() {
        //TODO
//        assertTrue(false);
    }

    @Test
    @WithMockUser(roles = "Admin")
    void updateCourse() {
        long courseId = preloadedCourses.getFirst().getId();
        CourseRequest courseRequest = new CourseRequest(
                "NewTestTitle",
                "NewTestDescription",
                12,
                null
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

        assertEquals(12, updatedCourse.get().getAiSessionTtlWeeks(), "Wrong TTL weeks");
    }

    @Test
    @WithMockUser(roles = "Admin")
    void deleteCourse() {
        Mockito.doNothing().when(blobStorageService).deleteSectionFiles(Mockito.anyLong());

        Course course = preloadedCourses.getFirst();
        long courseId = course.getId();
        loadSections(course);

        ResponseEntity<Void> responseEntity = controller.deleteCourse(courseId);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode(), "Wrong status code");

        assertFalse(courseRepository.findById(courseId).isPresent(), "Course not deleted");
        assertEquals(NUMBER_OF_COURSES-1, courseRepository.findAll().size(), "Wrong number of courses");

        List<Section> sections = sectionRepository.findAll();
        assertNotNull(sections, "Section list is null");
        assertTrue(sections.isEmpty(), "Section List should be empty");
    }

    @Test
    void getProgress() {
        //TODO
//        assertTrue(false);
    }

    //Helpers

    private void checkCourseResponse(Course course, CourseResponse courseResponse){
        assertNotNull(courseResponse, "Course response is null");
        assertEquals(course.getId(), courseResponse.getId(), "Wrong course id");
        assertEquals(course.getTitle(), courseResponse.getTitle(), "Wrong course title");
        assertEquals(course.getDescription(), courseResponse.getDescription(), "Wrong course description");
        assertEquals(course.getAiSessionTtlWeeks(), courseResponse.getAiSessionTtlWeeks(), "Wrong TTL weeks");
    }

    private List<Section> loadSections(Course course) {
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            Section section = Section.builder()
                    .title("TestSection" + i)
                    .orderIndex(i)
                    .course(course)
                    .build();
            sections.add(section);
        }
        return sectionRepository.saveAll(sections);
    }

    private List<User> loadUsers() {
        List<User> userList = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_COURSES; i++) {
            User user = new User();
            user.setEntraId("Entra" + i);
            userList.add(user);
            Mockito.when(graphService.getUserByEntraId(Mockito.any())).thenReturn(new GraphResponse(
                    user.getEntraId(),
                    "1",
                    "A",
                    "B",
                    "C",
                    Set.of()
            ));
        }
        return userRepository.saveAll(userList);
    }
}