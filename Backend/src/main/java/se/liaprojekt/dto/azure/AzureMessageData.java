package se.liaprojekt.dto.azure;

import java.util.List;

public record AzureMessageData(
        List<AzureMessageContent> content
) {
}