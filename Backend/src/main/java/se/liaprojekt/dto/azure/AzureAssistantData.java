package se.liaprojekt.dto.azure;

public record AzureAssistantData(
        String id,
        String name,
        String instructions,
        String model
) {
}