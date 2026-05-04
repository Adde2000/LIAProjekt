package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.service.TokenService;

import java.util.Map;
import se.liaprojekt.dto.CourseResponse;
import se.liaprojekt.dto.UserResponse;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final TokenService tokenService;

    @GetMapping("/all")
    public ResponseEntity<String> getAllUsers() {
        RestTemplate restTemplate = new RestTemplate();
        System.out.println(tokenService.getAccessToken(restTemplate));
        return ResponseEntity.ok("OK - getAllUsers");
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