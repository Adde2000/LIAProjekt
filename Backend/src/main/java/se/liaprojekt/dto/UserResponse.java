package se.liaprojekt.dto;

import java.util.Set;

public record UserResponse(
        Long id,
        String displayName,
        String givenName,
        String surname,
        String mail,
        Set<String> role
) {
}
