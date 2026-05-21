package se.liaprojekt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response tillbaka till frontend chat UI.
 */
@Getter
@AllArgsConstructor
public class ChatResponse {

    @Schema(
            example = "Hej! Jag är din kurs-assistent."
    )
    private String response;
}