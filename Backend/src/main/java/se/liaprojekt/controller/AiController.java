package se.liaprojekt.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @PostMapping("/chat")
    public String chat() {
        return "OK - chat";
    }

    @GetMapping("/characters/{courseId}")
    public String getCharacters(@PathVariable Long courseId) {
        return "OK - getCharacters " + courseId;
    }

    @PostMapping("/session")
    public String startSession() {
        return "OK - startSession";
    }
}