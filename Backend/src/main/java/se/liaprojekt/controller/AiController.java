package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.dto.*;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.ai.AiCharacterService;
import se.liaprojekt.service.ai.AiChatService;
import se.liaprojekt.service.ai.AiSessionInitService;
import se.liaprojekt.service.ai.AssistantAdminService;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiChatService aiChatService;
    private final AiSessionInitService initService;
    private final AiCharacterService aiCharacterService;
    private final AssistantAdminService assistantAdminService;
    private final CurrentUserService currentUserService;

    /**
     * Send message to Azure Assistant thread
     */
    //ALL
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request
    ) {
        log.info("Chat request for sessionId={}", request.getSessionId());
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
    //ALL
    @PostMapping("/session")
    public ResponseEntity<Long> createSession(
            @RequestParam Long courseId
    ) {

        String entraId = currentUserService.getEntraId();
        log.info("Creating AI session for courseId={}", courseId);

        AiSession session = initService.createSession(
                entraId,
                courseId
        );
        log.info("AI session id={} created for courseId={}", session.getId(), courseId);

        return ResponseEntity.ok(
                session.getId()
        );
    }

    /**
     * Get available AI characters for a course
     */
    //ALL
    @GetMapping("/characters/{courseId}")
    public ResponseEntity<List<AiCharacterResponse>> getCharacters(
            @PathVariable Long courseId
    ) {
        log.info("Getting AI characters for courseId={}", courseId);
        List<AiCharacterResponse> characters =
                aiCharacterService.getByCourse(courseId);

        return ResponseEntity.ok(characters);
    }

    /**
     * Get all Azure OpenAI assistants
     */
    //(Admin/CourseAdmin)
    @GetMapping("/assistants")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<List<AssistantAdminResponse>> getAssistants() {
        log.info("Getting all Azure OpenAI assistants");
        return ResponseEntity.ok(
                assistantAdminService.getAllAssistants()
        );
    }

    //ALL
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatHistoryMessage>> getHistory(
            @PathVariable Long sessionId
    ) {
        log.info("Getting chat history for sessionId={}", sessionId);
        return ResponseEntity.ok(
                aiChatService.getHistory(sessionId)
        );
    }
}