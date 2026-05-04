package se.liaprojekt.dto;

public record UserResponse(
        String id,
        String displayName,
        String givenName,
        String surname,
        String mail,
        String memberOf
) {
}
