package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.service.TokenService;

import java.util.Map;

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
    public ResponseEntity<String> getCurrentUser() {
        return ResponseEntity.ok("OK - getCurrentUser");
    }

    @GetMapping("/me/courses")
    public ResponseEntity<String> getMyCourses() {
        return ResponseEntity.ok("OK - getMyCourses");
    }

    @PostMapping("/enroll/{courseId}")
    public ResponseEntity<String> enrollInCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok("OK - enrollInCourse " + courseId);
    }
}