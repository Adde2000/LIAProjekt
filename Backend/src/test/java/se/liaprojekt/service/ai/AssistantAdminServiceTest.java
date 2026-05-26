package se.liaprojekt.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.dto.AssistantAdminResponse;
import se.liaprojekt.dto.azure.AzureAssistantData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantAdminServiceTest {

    @Mock
    private AzureAssistantClient azureAssistantClient;

    @InjectMocks
    private AssistantAdminService service;

    @Test
    void shouldReturnAllAssistants() {

        // Arrange
        AzureAssistantData assistant =
                new AzureAssistantData(
                        "assistant-1",
                        "Math Tutor",
                        "Helpful assistant",
                        "gpt-4"
                );

        when(azureAssistantClient.getAssistants())
                .thenReturn(List.of(assistant));

        // Act
        List<AssistantAdminResponse> result =
                service.getAllAssistants();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        AssistantAdminResponse response =
                result.get(0);

        assertEquals(
                "assistant-1",
                response.getId()
        );

        assertEquals(
                "Math Tutor",
                response.getName()
        );

        assertEquals(
                "Helpful assistant",
                response.getInstructions()
        );

        assertEquals(
                "gpt-4",
                response.getModel()
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoAssistantsExist() {

        // Arrange
        when(azureAssistantClient.getAssistants())
                .thenReturn(List.of());

        // Act
        List<AssistantAdminResponse> result =
                service.getAllAssistants();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}