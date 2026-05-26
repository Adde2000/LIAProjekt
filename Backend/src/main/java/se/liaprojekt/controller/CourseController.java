package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.*;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.SectionService;
import se.liaprojekt.service.CurrentUserService;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {
    Logger logger = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;
    private final SectionService sectionService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourseById(courseId));
    }

    @GetMapping("/{courseId}/students")
    public ResponseEntity<List<UserResponse>> getCourseStudents(@PathVariable Long courseId) {
        logger.info("Get student list for course: {}", courseId);
        return ResponseEntity.ok(courseService.getStudentsInCourse(courseId));
    }

    @PostMapping("/{courseId}/students")
    public ResponseEntity<List<UserResponse>> addStudentsToCourse(@PathVariable Long courseId, @RequestBody List<UserRequest> students) {
        logger.info("Adding students to course {}", courseId);
        return ResponseEntity.ok(courseService.addStudentsToCourse(courseId, students));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest courseRequest) {
        logger.info("Creating new course {}", courseRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(courseRequest));
    }

    @PostMapping("/{courseId}/sections")
    public ResponseEntity<SectionResponse> addSection(
            @PathVariable Long courseId,
            @RequestBody SectionRequest request) {

        return ResponseEntity.ok(
                sectionService.addSection(courseId, request.title())
        );
    }

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
        //TODO
        return ResponseEntity.ok("OK - completeCourse " + courseId);
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable Long courseId,
            @RequestBody CourseRequest courseRequest) {

        return ResponseEntity.ok(courseService.updateCourse(courseId, courseRequest));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long courseId) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}/progress")
    public ResponseEntity<CourseProgressResponse> getProgress(
            @PathVariable Long courseId
    ) {
        String entraId = currentUserService.getEntraId();

        return ResponseEntity.ok(
                courseService.getCourseProgress(courseId, entraId)
        );
    }

    @PutMapping("/{courseId}/assistant/{assistantId}")
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