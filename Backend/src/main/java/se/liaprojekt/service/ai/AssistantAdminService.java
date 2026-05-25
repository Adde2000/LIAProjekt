package se.liaprojekt.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.AssistantAdminResponse;
import se.liaprojekt.dto.azure.AzureAssistantData;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssistantAdminService {

    private final AzureAssistantClient azureAssistantClient;

    public List<AssistantAdminResponse> getAllAssistants() {

        return azureAssistantClient.getAssistants()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AssistantAdminResponse toResponse(
            AzureAssistantData assistant
    ) {

        return new AssistantAdminResponse(
                assistant.id(),
                assistant.name(),
                assistant.instructions(),
                assistant.model()
        );
    }
}