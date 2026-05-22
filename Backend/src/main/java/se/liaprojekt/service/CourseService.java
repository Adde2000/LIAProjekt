package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.liaprojekt.dto.*;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.SectionRepository;
import se.liaprojekt.repository.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.liaprojekt.repository.UserProgressRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Logger log =
            LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final TestResultRepository testResultRepository;
    private final SectionRepository sectionRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserService userService;

    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<CourseResponse> getAllRegisteredCourses(long userId) {
        List<UserProgress> userProgressList = userProgressRepository.findByUserId(userId);
        List<CourseResponse> courseResponseList = new ArrayList<>();
        for (UserProgress userProgress : userProgressList) {
            Course course = userProgress.getCourse();
            courseResponseList.add(mapToResponse(course));
        }
        return courseResponseList;
    }

    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course not found with id: " + id)
                );

        return mapToResponse(course);
    }

    public CourseResponse createCourse(CourseRequest request) {
        Course course = new Course();
        course.setTitle(request.title());
        course.setDescription(request.description());
        // TODO: ändra CreatedBy till authenticatied user
        course.setCreatedBy("system");

        Course saved = courseRepository.save(course);

        return mapToResponse(saved);
    }

    //TODO return UserProgressResponse
    public List<UserResponse> addStudentsToCourse(Long courseId, List<UserRequest> students) {
        Course course = courseRepository.findById(courseId).orElseThrow(() ->
                new ResourceNotFoundException("Course not found with id: " + courseId));
        List<UserProgress> userProgressList = new ArrayList<>();
        students.forEach(student -> {
            userProgressList.add(new UserProgress(userService.getUserById(student.id()), course));
        });
        userProgressRepository.saveAll(userProgressList);
        return getStudentsInCourse(courseId);
    }

    //TODO return UserProgressResponse
    public List<UserResponse> getStudentsInCourse(Long courseId) {
        List<UserProgress> userProgressList = userProgressRepository.findByCourseId(courseId);
        List<UserResponse> userResponseList = new ArrayList<>();
        userProgressList.forEach(userProgress -> {
            userResponseList.add(userService.getUserResponseById(userProgress.getUser().getId()));
        });
        return userResponseList;
    }

    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course not found with id: " + id)
                );

        course.setTitle(request.title());
        course.setDescription(request.description());

        return mapToResponse(courseRepository.save(course));
    }

    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course not found with id: " + id)
                );

        courseRepository.delete(course);
    }

    private CourseResponse mapToResponse(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCreatedBy()
        );
    }

    public boolean isCourseCompleted(String entraId, Course course) {

        log.debug("CHECK COURSE COMPLETION | entraId={} courseId={}",
                entraId,
                course.getId()
        );

        List<Section> sections = sectionRepository.findByCourseId(course.getId());

        for (Section section : sections) {

            TestResult lastAttempt = testResultRepository
                    .findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                            entraId,
                            section.getId()
                    )
                    .orElse(null);

            log.debug("SECTION CHECK | sectionId={} status={}",
                    section.getId(),
                    lastAttempt != null ? lastAttempt.getStatus() : "NULL"
            );

            if (lastAttempt == null ||
                    lastAttempt.getStatus() != TestResult.Status.COMPLETED) {
                return false;
            }
        }

        return true;
    }

    // =========================
    // GET COURSE PROGRESS
    // =========================
    public CourseProgressResponse getCourseProgress(Long courseId, String entraId) {

        // =========================
        // FETCH COURSE
        // =========================
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        List<Section> sections = course.getSections();

        int totalSections = sections.size();

        // =========================
        // COUNT COMPLETED SECTIONS (BASED ON LAST ATTEMPT)
        // =========================
        int completedSections = (int) sections.stream()
                .filter(section -> {

                    // =========================
                    // FETCH LAST ATTEMPT (IMPORTANT)
                    // =========================
                    TestResult lastAttempt = testResultRepository
                            .findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                                    entraId,
                                    section.getId()
                            )
                            .orElse(null);

                    // =========================
                    // SECTION IS COMPLETED ONLY IF LAST ATTEMPT IS COMPLETED
                    // =========================
                    return lastAttempt != null &&
                            lastAttempt.getStatus() == TestResult.Status.COMPLETED;
                })
                .count();

        // =========================
        // CALCULATE PROGRESS %
        // =========================
        int progress = totalSections == 0
                ? 0
                : (int) ((completedSections * 100.0) / totalSections);

        return new CourseProgressResponse(
                course.getId(),
                course.getTitle(),
                totalSections,
                completedSections,
                progress
        );
    }

    @Transactional
    public void assignAssistant(
            Long courseId,
            String assistantId
    ) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow();

        course.setAssistantId(assistantId);

        courseRepository.save(course);
    }
}
