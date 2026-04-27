package se.liaprojekt.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public String getCurrentUser() {
        return "OK - getCurrentUser";
    }

    @GetMapping("/me/courses")
    public String getMyCourses() {
        return "OK - getMyCourses";
    }

    @PostMapping("/enroll/{courseId}")
    public String enrollInCourse(@PathVariable Long courseId) {
        return "OK - enrollInCourse " + courseId;
    }
}