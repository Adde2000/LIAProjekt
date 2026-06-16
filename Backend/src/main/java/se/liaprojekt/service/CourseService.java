package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.liaprojekt.dto.*;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.liaprojekt.service.ai.AssistantAdminService;
import se.liaprojekt.service.ai.VectorStoreService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final SectionService sectionService;
    private final CurrentUserService currentUserService;
    private final VectorStoreService vectorStoreService;
    private final AiSessionRepository aiSessionRepository;
    private final AssistantAdminService assistantAdminService;

    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<CourseResponse> getAllCourses(Long courseAdminId) {
        return courseRepository.findByCourseAdminId(courseAdminId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<Map<String, Object>> getAllRegisteredCourses(long userId) {
        List<UserProgress> userProgressList = userProgressRepository.findByUser_Id(userId);
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (UserProgress userProgress : userProgressList) {
            Course course = userProgress.getCourse();
            Map<String, Object> response = Map.of(
                    "courseResponse", mapToResponse(course),
                    "userProgressResponse", mapToResponse(userProgress)
            );
            responseList.add(response);
        }
        return responseList;
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
        course.setAiSessionTtlWeeks(
                request.aiSessionTtlWeeks() != null
                        ? request.aiSessionTtlWeeks()
                        : 6
        );
        if(request.courseAdminId() != null) {
            course.setCourseAdmin(userService.getUserById(request.courseAdminId()));
        }

        // namn från JWT token
        course.setCreatedBy(
                currentUserService.getName()
        );



        Course saved = courseRepository.save(course);

        // Skapa vector store direkt
        String vectorStoreId = vectorStoreService.createVectorStore(course.getTitle());
        course.setVectorStoreId(vectorStoreId);
        courseRepository.save(course);

        return mapToResponse(saved);
    }

    public List<UserProgressResponse> addStudentsToCourse(Long courseId, List<UserRequest> students) {
        Course course = courseRepository.findById(courseId).orElseThrow(() ->
                new ResourceNotFoundException("Course not found with id: " + courseId));
        List<UserProgress> userProgressList = new ArrayList<>();
        students.forEach(student -> {
            userProgressList.add(new UserProgress(userService.getUserById(student.id()), course));
        });
        userProgressRepository.saveAll(userProgressList);
        return getStudentsInCourse(courseId);
    }

    public List<UserProgressResponse> getStudentsInCourse(Long courseId) {
        List<UserProgress> userProgressList = userProgressRepository.findByCourseId(courseId);
        List<UserProgressResponse> userProgressResponseList = new ArrayList<>();
        userProgressList.forEach(userProgress -> {
            userProgressResponseList.add(mapToResponse(userProgress));
        });
        return userProgressResponseList;
    }

    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course not found with id: " + id)
                );

        if(request.title() != null) {
            course.setTitle(request.title());
        }
        if(request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.aiSessionTtlWeeks() != null) {
            course.setAiSessionTtlWeeks(request.aiSessionTtlWeeks());
        }
        if(request.courseAdminId() != null) {
            course.setCourseAdmin(userService.getUserById(request.courseAdminId()));
        }

        return mapToResponse(courseRepository.save(course));
    }

    @Transactional
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course not found with id: " + id)
                );

        // Ta bort AI-sessioner först
        aiSessionRepository.deleteByCourseId(id);

        List<Long> sectionIds = course.getSections()
                .stream()
                .map(Section::getId)
                .toList();

        for (Long sectionId : sectionIds) {
            sectionService.deleteSection(sectionId);
        }
        sectionRepository.flush();

        courseRepository.delete(course);

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

    public Course getCourseEntity(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
    }

    public Course getCourseBySection(Long sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Section not found: " + sectionId
                ))
                .getCourse();
    }

    private CourseResponse mapToResponse(Course course) {
        UserResponse courseAdminResponse = null;
        if (course.getCourseAdmin() != null) {
            courseAdminResponse = userService.getUserResponseById(course.getCourseAdmin().getId());
        }

        String assistantName = assistantAdminService.getAssistantName(course.getAssistantId());

        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCreatedBy(),
                course.getAiSessionTtlWeeks(),
                courseAdminResponse,
                course.getAssistantId(),
                assistantName
        );
    }

    private UserProgressResponse mapToResponse(UserProgress userProgress) {
        return new UserProgressResponse(
                userService.getUserResponseById(userProgress.getUser().getId()),
                userProgress.getCompletedSections(),
                userProgress.getProgressPercentage()
        );
    }
}
