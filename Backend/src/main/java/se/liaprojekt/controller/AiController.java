package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.AiCharacterResponse;
import se.liaprojekt.dto.ChatRequest;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.service.AiCharacterService;
import se.liaprojekt.service.AiChatService;
import se.liaprojekt.service.AiSessionInitService;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService chatService;
    private final AiSessionInitService initService;
    private final AiCharacterService aiCharacterService;

    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {

        return ResponseEntity.ok(
                chatService.chat(
                        request.getSessionId(),
                        request.getMessage()
                )
        );
    }

    @PostMapping("/session")
    public ResponseEntity<Long> createSession(
            @RequestParam Long userId,
            @RequestParam Long courseId,
            @RequestParam Long characterId
    ) {

        AiSession session = initService.createSession(
                userId,
                courseId,
                characterId
        );

        return ResponseEntity.ok(session.getId());
    }


    /**
     * Fetch available characters per course
     */
    @GetMapping("/characters/{courseId}")
    public ResponseEntity<List<AiCharacterResponse>> getCharacters(@PathVariable Long courseId) {

        return ResponseEntity.ok(
                aiCharacterService.getByCourse(courseId)
        );
    }
}