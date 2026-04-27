package se.liaprojekt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @GetMapping
    public ResponseEntity<String> getAllCourses() {
        return ResponseEntity.ok("OK - getAllCourses");
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<String> getCourseById(@PathVariable Long courseId) {
        return ResponseEntity.ok("OK - getCourseById " + courseId);
    }

    @GetMapping("/{courseId}/students")
    public ResponseEntity<String> getCourseStudents(@PathVariable Long courseId) {
        return ResponseEntity.ok("OK - getCourseStudents " + courseId);
    }

    @PostMapping
    public ResponseEntity<String> createCourse() {
        return ResponseEntity.ok("OK - createCourse");
    }

    @PostMapping("/{courseId}/sections")
    public ResponseEntity<String> addSection(@PathVariable Long courseId) {
        return ResponseEntity.ok("OK - addSection " + courseId);
    }

    @PostMapping("/{courseId}/complete")
    public ResponseEntity<String> completeCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok("OK - completeCourse " + courseId);
    }
}