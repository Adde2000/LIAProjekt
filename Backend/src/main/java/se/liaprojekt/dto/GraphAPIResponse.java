package se.liaprojekt.dto;

import java.util.List;

public record GraphAPIResponse(
        List<UserResponse> value
) {
}
