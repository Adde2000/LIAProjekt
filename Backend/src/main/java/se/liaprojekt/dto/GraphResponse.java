package se.liaprojekt.dto;

public record GraphResponse(
        String id,
        String displayName,
        String givenName,
        String surname,
        String mail
) {
}
