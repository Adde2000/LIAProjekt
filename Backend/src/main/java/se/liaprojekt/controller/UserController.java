package se.liaprojekt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.dto.UserResponse;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        //TODO
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        //TODO
        return ResponseEntity.ok(null);
    }

    @GetMapping("/me/courses")
    public ResponseEntity<List<CourseResponse>> getMyCourses() {
        //TODO
        return ResponseEntity.ok(List.of());
    }

}