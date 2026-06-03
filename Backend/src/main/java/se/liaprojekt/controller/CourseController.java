package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.dto.*;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.SectionService;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.UserService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {
    Logger logger = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;
    private final SectionService sectionService;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    //(Admin/CourseAdmin)
    @GetMapping
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        Set<String> roles = currentUserService.getRoles();
        if (roles.contains(Roles.ADMIN)) {
            return ResponseEntity.ok(courseService.getAllCourses());
        } else {
            String entraId = currentUserService.getEntraId();
            long userId = userService.getUserByEntraId(entraId).getId();
            return ResponseEntity.ok(courseService.getAllCourses(userId));
        }
    }

    //(Admin/CourseAdmin)
    @GetMapping("/{courseId}")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourseById(courseId));
    }

    //(Admin/CourseAdmin)
    @GetMapping("/{courseId}/students")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<List<UserProgressResponse>> getCourseStudents(@PathVariable Long courseId) {
        logger.info("Get student list for course: {}", courseId);
        return ResponseEntity.ok(courseService.getStudentsInCourse(courseId));
    }

    //(Admin)
    @PostMapping("/{courseId}/students")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<List<UserProgressResponse>> addStudentsToCourse(@PathVariable Long courseId, @RequestBody List<UserRequest> students) {
        logger.info("Adding students to course {}", courseId);
        return ResponseEntity.ok(courseService.addStudentsToCourse(courseId, students));
    }

    //Admin
    @PostMapping
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest courseRequest) {
        logger.info("Creating new course {}", courseRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(courseRequest));
    }

    //(Admin/CourseAdmin)
    @PostMapping("/{courseId}/sections")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<SectionResponse> addSection(
            @PathVariable Long courseId,
            @RequestBody SectionRequest request) {

        return ResponseEntity.ok(
                sectionService.addSection(courseId, request.title())
        );
    }

    //ALL
    @GetMapping("/{courseId}/sections")
    public ResponseEntity<List<SectionResponse>> getSections(
            @PathVariable Long courseId) {

        String entraId = currentUserService.getEntraId();

        return ResponseEntity.ok(
                sectionService.getSections(courseId, entraId)
        );
    }

    @PostMapping("/{courseId}/complete")
    public ResponseEntity<String> completeCourse(@PathVariable Long courseId) {
        //TODO is this needed?
        return ResponseEntity.ok("OK - completeCourse " + courseId);
    }

    //(Admin/CourseAdmin)
    @PutMapping("/{courseId}")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable Long courseId,
            @RequestBody CourseRequest courseRequest) {

        return ResponseEntity.ok(courseService.updateCourse(courseId, courseRequest));
    }

    //Admin
    @DeleteMapping("/{courseId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<Void> deleteCourse(@PathVariable Long courseId) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    //ALL
    @GetMapping("/{courseId}/progress")
    public ResponseEntity<CourseProgressResponse> getProgress(
            @PathVariable Long courseId
    ) {
        String entraId = currentUserService.getEntraId();

        return ResponseEntity.ok(
                courseService.getCourseProgress(courseId, entraId)
        );
    }

    //(Admin/CourseAdmin)
    @PutMapping("/{courseId}/assistant/{assistantId}")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<Void> assignAssistant(
            @PathVariable Long courseId,
            @PathVariable String assistantId
    ) {

        courseService.assignAssistant(
                courseId,
                assistantId
        );

        return ResponseEntity.ok().build();
    }
}