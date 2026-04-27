package se.liaprojekt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @PostMapping("/chat")
    public ResponseEntity<String> chat() {
        return ResponseEntity.ok("OK - chat");
    }

    @GetMapping("/characters/{courseId}")
    public ResponseEntity<String> getCharacters(@PathVariable Long courseId) {
        return ResponseEntity.ok("OK - getCharacters " + courseId);
    }

    @PostMapping("/session")
    public ResponseEntity<String> startSession() {
        return ResponseEntity.ok("OK - startSession");
    }
}