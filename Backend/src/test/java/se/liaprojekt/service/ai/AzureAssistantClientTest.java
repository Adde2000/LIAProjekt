package se.liaprojekt.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.dto.ChatHistoryMessage;
import se.liaprojekt.dto.azure.*;
import se.liaprojekt.exception.AzureAssistantException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureAssistantClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AzureAssistantClient client;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(
                client,
                "endpoint",
                "https://test.openai.azure.com"
        );

        ReflectionTestUtils.setField(
                client,
                "apiVersion",
                "2024-05-01"
        );

        ReflectionTestUtils.setField(
                client,
                "maxPollAttempts",
                1
        );

        ReflectionTestUtils.setField(
                client,
                "pollIntervalMs",
                1L
        );
    }

    @Test
    void shouldCreateThread() {

        AzureThreadResponse response =
                new AzureThreadResponse("thread-123");

        when(restTemplate.exchange(
                contains("/openai/threads"),
                eq(HttpMethod.POST),
                any(),
                eq(AzureThreadResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        String threadId = client.createThread("vs-test-123");

        assertEquals("thread-123", threadId);
    }

    @Test
    void shouldThrowWhenCreateThreadReturnsInvalidResponse() {

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(),
                eq(AzureThreadResponse.class)
        )).thenReturn(ResponseEntity.ok(null));

        assertThrows(
                AzureAssistantException.class,
                () -> client.createThread("vs-test-123")
        );
    }

    @Test
    void shouldCreateRun() {

        AzureRunResponse response =
                new AzureRunResponse("run-123", "started");

        when(restTemplate.exchange(
                contains("/runs"),
                eq(HttpMethod.POST),
                any(),
                eq(AzureRunResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        String runId = client.createRun("thread-1", "assistant-1");

        assertEquals("run-123", runId);
    }

    @Test
    void shouldGetRunStatus() {

        AzureRunStatusResponse response =
                new AzureRunStatusResponse("run-1", "completed");

        when(restTemplate.exchange(
                contains("/runs/"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureRunStatusResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        String status = client.getRunStatus("thread-1", "run-1");

        assertEquals("completed", status);
    }

    @Test
    void shouldGetLatestMessage() {

        AzureTextContent text =
                new AzureTextContent("Hello from AI");

        AzureMessageContent content =
                new AzureMessageContent(text);

        AzureMessageData message =
                new AzureMessageData("msg-1", "assistant", List.of(content));

        AzureMessageListResponse response =
                new AzureMessageListResponse(List.of(message));

        when(restTemplate.exchange(
                contains("/messages"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureMessageListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        String result = client.getLatestMessage("thread-1");

        assertEquals("Hello from AI", result);
    }

    @Test
    void shouldThrowWhenNoMessagesReturned() {

        AzureMessageListResponse response =
                new AzureMessageListResponse(List.of());

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(AzureMessageListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        assertThrows(
                AzureAssistantException.class,
                () -> client.getLatestMessage("thread-1")
        );
    }

    @Test
    void shouldGetAssistants() {

        AzureAssistantData assistant =
                new AzureAssistantData("assistant-1", "Math Tutor", "Helpful", "gpt-4");

        AzureAssistantListResponse response =
                new AzureAssistantListResponse(List.of(assistant));

        when(restTemplate.exchange(
                contains("/assistants"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureAssistantListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        List<AzureAssistantData> result = client.getAssistants();

        assertEquals(1, result.size());
        assertEquals("Math Tutor", result.get(0).name());
    }

    @Test
    void shouldGetThreadMessages() {

        AzureTextContent text =
                new AzureTextContent("Hej");

        AzureMessageContent content =
                new AzureMessageContent(text);

        AzureMessageData message =
                new AzureMessageData("msg-2", "user", List.of(content));

        AzureMessageListResponse response =
                new AzureMessageListResponse(List.of(message));

        when(restTemplate.exchange(
                contains("/messages"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureMessageListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        List<ChatHistoryMessage> result =
                client.getThreadMessages("thread-1");

        assertEquals(1, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("Hej", result.get(0).getContent());
    }

    @Test
    void shouldThrowWhenRestTemplateFails() {

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(),
                eq(AzureThreadResponse.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(
                AzureAssistantException.class,
                () -> client.createThread("vs-test-123")
        );
    }
}