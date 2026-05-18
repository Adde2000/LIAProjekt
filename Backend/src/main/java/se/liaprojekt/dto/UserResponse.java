package se.liaprojekt.dto;

public record UserResponse(
        Long id,
        String displayName,
        String givenName,
        String surname,
        String mail,
        String role
) {
}
