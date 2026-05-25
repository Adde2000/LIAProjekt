package se.liaprojekt.dto.azure;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateRunRequest(

        @JsonProperty("assistant_id")
        String assistantId
) {
}