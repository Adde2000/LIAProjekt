package se.liaprojekt.dto.azure;

import java.util.List;

public record AzureMessageListResponse(
        List<AzureMessageData> data
) {
}