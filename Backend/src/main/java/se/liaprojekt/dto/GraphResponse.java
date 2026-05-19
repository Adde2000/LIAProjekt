package se.liaprojekt.dto;

import java.util.Set;

public record GraphResponse(
        String id,
        String displayName,
        String givenName,
        String surname,
        String mail,
        Set<String> role
) {
}
