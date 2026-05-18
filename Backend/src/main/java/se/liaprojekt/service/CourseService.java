package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.CourseProgressResponse;
import se.liaprojekt.dto.CourseRequest;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.Section;
import se.liaprojekt.model.TestResult;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.SectionRepository;
import se.liaprojekt.repository.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Logger log =
            LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final TestResultRepository testResultRepository;
    private final SectionRepository sectionRepository;

    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
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
}
