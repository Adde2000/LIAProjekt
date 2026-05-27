package se.liaprojekt.dto.azure;

import java.util.List;

public record AzureAssistantListResponse(
        List<AzureAssistantData> data
) {
}