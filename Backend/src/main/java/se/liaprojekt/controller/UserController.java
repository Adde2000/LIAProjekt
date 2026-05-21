package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.dto.UserResponse;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final CourseService courseService;

    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> userResponseList = userService.getAllUserResponses();
        return ResponseEntity.ok(userResponseList);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable long userId) {
        UserResponse userResponse = userService.getUserResponseById(userId);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        //TODO
        return ResponseEntity.ok(null);
    }

    @GetMapping("/me/courses")
    public ResponseEntity<List<CourseResponse>> getMyCourses(@AuthenticationPrincipal Jwt jwt) {
        String entraId = jwt.getClaim("oid");
        long userId = userService.getUserByEntraId(entraId).getId();
        List<CourseResponse> courseResponseList = courseService.getAllRegisteredCourses(userId);
        return ResponseEntity.ok(courseResponseList);
    }

}