package se.liaprojekt.dto;

import java.util.List;

public record UserResponse(
        Long id,
        String displayName,
        String givenName,
        String surname,
        String mail,
        List<String> role
) {
}
