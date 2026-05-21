package se.liaprojekt.dto.azure;

public record AddMessageRequest(
        String role,
        String content
) {
}