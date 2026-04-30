package se.liaprojekt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.CourseRequest;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.dto.UserRequest;
import se.liaprojekt.dto.UserResponse;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        //TODO
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long courseId) {
        //TODO
        return ResponseEntity.ok(null);
    }

    @GetMapping("/{courseId}/students")
    public ResponseEntity<List<UserResponse>> getCourseStudents(@PathVariable Long courseId) {
        //TODO
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{courseId}/students")
    public ResponseEntity<String> addStudentsToCourse(@PathVariable Long courseId, @RequestBody List<UserRequest> students) {
        //TODO
        return ResponseEntity.ok("OK - getCourseStudents " + courseId);
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest courseRequest) {
        //TODO
        return ResponseEntity.ok(null);
    }

    @PostMapping("/{courseId}/sections")
    public ResponseEntity<String> addSection(@PathVariable Long courseId) {
        //TODO
        return ResponseEntity.ok("OK - addSection " + courseId);
    }

    @PostMapping("/{courseId}/complete")
    public ResponseEntity<String> completeCourse(@PathVariable Long courseId) {
        //TODO
        return ResponseEntity.ok("OK - completeCourse " + courseId);
    }
}