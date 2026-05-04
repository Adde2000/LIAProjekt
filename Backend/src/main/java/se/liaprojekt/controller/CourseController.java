package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.*;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.SectionService;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final SectionService sectionService;

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
        //TODO
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{courseId}/students")
    public ResponseEntity<List<UserResponse>> addStudentsToCourse(@PathVariable Long courseId, @RequestBody List<UserRequest> students) {
        //TODO
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest courseRequest) {
        return ResponseEntity.ok(courseService.createCourse(courseRequest));
    }

    @PostMapping("/{courseId}/sections")
    public ResponseEntity<String> addSection(
            @PathVariable Long courseId,
            @RequestBody SectionRequest request) {

        sectionService.addSection(courseId, request.title());
        return ResponseEntity.ok("Section created");
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
}