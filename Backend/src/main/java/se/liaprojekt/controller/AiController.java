package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.AiCharacterResponse;
import se.liaprojekt.dto.ChatRequest;
import se.liaprojekt.dto.ChatResponse;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.service.AiCharacterService;
import se.liaprojekt.service.AiChatService;
import se.liaprojekt.service.AiSessionInitService;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;
    private final AiSessionInitService initService;
    private final AiCharacterService aiCharacterService;

    /**
     * Send message to Azure Assistant thread
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request
    ) {

        String aiResponse = aiChatService.chat(
                request.getSessionId(),
                request.getMessage()
        );

        return ResponseEntity.ok(
                new ChatResponse(aiResponse)
        );
    }

    /**
     * Create or reuse session/thread
     */
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
     * Get available AI characters for a course
     */
    @GetMapping("/characters/{courseId}")
    public ResponseEntity<List<AiCharacterResponse>> getCharacters(
            @PathVariable Long courseId
    ) {

        List<AiCharacterResponse> characters =
                aiCharacterService.getByCourse(courseId);

        return ResponseEntity.ok(characters);
    }
}