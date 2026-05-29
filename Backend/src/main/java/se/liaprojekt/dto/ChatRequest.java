package se.liaprojekt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request från frontend chat UI.
 * Innehåller endast det som behövs per message.
 */
@Getter
@Setter
public class ChatRequest {

    @NotNull
    @Schema(example = "1")
    private Long sessionId;

    @NotBlank
    @Size(max = 5000)
    @Schema(
            example = "Vem är du?"
    )
    private String message;
}