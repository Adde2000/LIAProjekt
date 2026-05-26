package se.liaprojekt.service.ai;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
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
import reactor.core.publisher.Mono;
import se.liaprojekt.dto.ChatHistoryMessage;
import se.liaprojekt.dto.azure.*;
import se.liaprojekt.exception.AzureAssistantException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureAssistantClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TokenCredential credential;

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

        AccessToken token =
                new AccessToken(
                        "fake-token",
                        OffsetDateTime.now().plusHours(1)
                );

        lenient().when(
                credential.getToken(any(TokenRequestContext.class))
        ).thenReturn(Mono.just(token));
    }

    @Test
    void shouldCreateThread() {

        // Arrange
        AzureThreadResponse response =
                new AzureThreadResponse("thread-123");

        when(restTemplate.exchange(
                contains("/openai/threads"),
                eq(HttpMethod.POST),
                any(),
                eq(AzureThreadResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Act
        String threadId = client.createThread();

        // Assert
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
                () -> client.createThread()
        );
    }

    @Test
    void shouldCreateRun() {

        // Arrange
        AzureRunResponse response =
                new AzureRunResponse("run-123", "started");

        when(restTemplate.exchange(
                contains("/runs"),
                eq(HttpMethod.POST),
                any(),
                eq(AzureRunResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Act
        String runId =
                client.createRun(
                        "thread-1",
                        "assistant-1"
                );

        // Assert
        assertEquals("run-123", runId);
    }

    @Test
    void shouldGetRunStatus() {

        // Arrange
        AzureRunStatusResponse response =
                new AzureRunStatusResponse(
                        "run-1",
                        "completed"
                );

        when(restTemplate.exchange(
                contains("/runs/"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureRunStatusResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Act
        String status =
                client.getRunStatus(
                        "thread-1",
                        "run-1"
                );

        // Assert
        assertEquals("completed", status);
    }

    @Test
    void shouldGetLatestMessage() {

        // Arrange
        AzureTextContent text =
                new AzureTextContent("Hello from AI");

        AzureMessageContent content =
                new AzureMessageContent(text);

        AzureMessageData message =
                new AzureMessageData(
                        "msg-1",
                        "assistant",
                        List.of(content)
                );

        AzureMessageListResponse response =
                new AzureMessageListResponse(
                        List.of(message)
                );

        when(restTemplate.exchange(
                contains("/messages"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureMessageListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Act
        String result =
                client.getLatestMessage("thread-1");

        // Assert
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

        // Arrange
        AzureAssistantData assistant =
                new AzureAssistantData(
                        "assistant-1",
                        "Math Tutor",
                        "Helpful",
                        "gpt-4"
                );

        AzureAssistantListResponse response =
                new AzureAssistantListResponse(
                        List.of(assistant)
                );

        when(restTemplate.exchange(
                contains("/assistants"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureAssistantListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Act
        List<AzureAssistantData> result =
                client.getAssistants();

        // Assert
        assertEquals(1, result.size());

        assertEquals(
                "Math Tutor",
                result.get(0).name()
        );
    }

    @Test
    void shouldGetThreadMessages() {

        // Arrange
        AzureTextContent text =
                new AzureTextContent("Hej");

        AzureMessageContent content =
                new AzureMessageContent(text);

        AzureMessageData message =
                new AzureMessageData(
                        "msg-2",
                        "user",
                        List.of(content)
                );

        AzureMessageListResponse response =
                new AzureMessageListResponse(
                        List.of(message)
                );

        when(restTemplate.exchange(
                contains("/messages"),
                eq(HttpMethod.GET),
                any(),
                eq(AzureMessageListResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Act
        List<ChatHistoryMessage> result =
                client.getThreadMessages("thread-1");

        // Assert
        assertEquals(1, result.size());

        assertEquals(
                "user",
                result.get(0).getRole()
        );

        assertEquals(
                "Hej",
                result.get(0).getContent()
        );
    }

    @Test
    void shouldThrowWhenTokenFails() {

        when(credential.getToken(any()))
                .thenThrow(new RuntimeException());

        assertThrows(
                AzureAssistantException.class,
                () -> client.createThread()
        );
    }
}