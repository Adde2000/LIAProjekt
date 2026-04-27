package se.liaprojekt.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @GetMapping
    public String getAllCourses() {
        return "OK - getAllCourses";
    }

    @GetMapping("/{courseId}")
    public String getCourseById(@PathVariable Long courseId) {
        return "OK - getCourseById " + courseId;
    }

    @GetMapping("/{courseId}/students")
    public String getCourseStudents(@PathVariable Long courseId) {
        return "OK - getCourseStudents " + courseId;
    }

    @PostMapping
    public String createCourse() {
        return "OK - createCourse";
    }

    @PostMapping("/{courseId}/sections")
    public String addSection(@PathVariable Long courseId) {
        return "OK - addSection " + courseId;
    }

    @PostMapping("/{courseId}/complete")
    public String completeCourse(@PathVariable Long courseId) {
        return "OK - completeCourse " + courseId;
    }
}