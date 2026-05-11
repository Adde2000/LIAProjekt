package se.liaprojekt.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Request från frontend chat UI.
 * Innehåller endast det som behövs per message.
 */
@Getter
@Setter
public class ChatRequest {

    // Kopplar till en pågående AI-session (thread)
    private Long sessionId;

    // Användarens nya meddelande
    private String message;
}