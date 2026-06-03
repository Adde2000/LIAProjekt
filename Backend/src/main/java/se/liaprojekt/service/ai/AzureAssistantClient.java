package se.liaprojekt.service.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.dto.ChatHistoryMessage;
import se.liaprojekt.dto.azure.*;
import se.liaprojekt.exception.AzureAssistantException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureAssistantClient {

    private static final String AZURE_SCOPE =
            "https://cognitiveservices.azure.com/.default";

    @Value("${azure.openai.poll.max-attempts}")
    private int maxPollAttempts;

    @Value("${azure.openai.poll.interval-ms}")
    private long pollIntervalMs;

    private final RestTemplate restTemplate;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-version}")
    private String apiVersion;

    @Value("${azure.openai.api-key}")
    private String apiKey;

    // =========================
    // VALIDATE CONFIG
    // =========================

    @PostConstruct
    public void validateConfig() {

        if (endpoint == null || endpoint.isBlank()) {

            throw new IllegalStateException(
                    "azure.openai.endpoint is missing"
            );
        }

        if (apiVersion == null || apiVersion.isBlank()) {

            throw new IllegalStateException(
                    "azure.openai.api-version is missing"
            );
        }

        log.info("Azure OpenAI endpoint configured");
    }

    // =========================
    // HEADERS
    // =========================

    private HttpHeaders headers() {

        HttpHeaders headers = new HttpHeaders();

        headers.set("api-key", apiKey);

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        return headers;
    }

    // =========================
    // URL BUILDER
    // =========================

    private String url(String path) {

        return endpoint +
                path +
                "?api-version=" +
                apiVersion;
    }

    // =========================
    // THREAD
    // =========================

    public String createThread(String vectorStoreId) {

        log.info(
                "Creating Azure OpenAI thread with vector store {}",
                vectorStoreId
        );

        Map<String, Object> body = Map.of(
                "tool_resources", Map.of(
                        "file_search", Map.of(
                                "vector_store_ids",
                                List.of(vectorStoreId)
                        )
                )
        );

        AzureThreadResponse response = post(
                url("/openai/threads"),
                body,
                AzureThreadResponse.class
        );

        if (response == null ||
                response.id() == null) {

            throw new AzureAssistantException(
                    "Azure returned invalid thread response"
            );
        }

        return response.id();
    }

    // =========================
    // MESSAGE
    // =========================

    public void addMessage(
            String threadId,
            String message
    ) {

        log.info(
                "Adding message to thread {}",
                threadId
        );

        AddMessageRequest request =
                new AddMessageRequest(
                        "user",
                        message
                );

        post(
                url("/openai/threads/" + threadId + "/messages"),
                request,
                Void.class
        );
    }

    // =========================
    // RUN
    // =========================

    public String createRun(
            String threadId,
            String assistantId
    ) {

        log.info("Creating assistant run");

        CreateRunRequest request =
                new CreateRunRequest(
                        assistantId
                );

        AzureRunResponse response = post(
                url("/openai/threads/" + threadId + "/runs"),
                request,
                AzureRunResponse.class
        );

        if (response == null ||
                response.id() == null) {

            throw new AzureAssistantException(
                    "Azure returned invalid run response"
            );
        }

        return response.id();
    }

    // =========================
    // WAIT FOR COMPLETION
    // =========================

    public String waitForCompletion(
            String threadId,
            String runId
    ) {

        int attempts = 0;

        while (attempts < maxPollAttempts) {

            String status =
                    getRunStatus(threadId, runId);

            log.info(
                    "Run status: {}",
                    status
            );

            // =========================
            // COMPLETED
            // =========================

            if ("completed".equals(status)) {

                return getLatestMessage(threadId);
            }

            // =========================
            // FAILED STATES
            // =========================

            if (List.of(
                    "failed",
                    "expired",
                    "cancelled"
            ).contains(status)) {

                throw new AzureAssistantException(
                        "Azure run failed with status: " +
                                status
                );
            }

            // =========================
            // UNKNOWN STATES
            // =========================

            if (!List.of(
                    "queued",
                    "in_progress",
                    "completed"
            ).contains(status)) {

                log.warn(
                        "Unhandled Azure run status: {}",
                        status
                );
            }

            sleep(pollIntervalMs);

            attempts++;
        }

        throw new AzureAssistantException(
                "Azure run timeout"
        );
    }

    // =========================
    // STATUS
    // =========================

    public String getRunStatus(
            String threadId,
            String runId
    ) {

        AzureRunStatusResponse response = get(
                url(
                        "/openai/threads/" +
                                threadId +
                                "/runs/" +
                                runId
                ),
                AzureRunStatusResponse.class
        );

        if (response == null ||
                response.status() == null) {

            throw new AzureAssistantException(
                    "Azure returned invalid run status response"
            );
        }

        return response.status();
    }

    // =========================
    // GET RESPONSE
    // =========================

    public String getLatestMessage(
            String threadId
    ) {

        AzureMessageListResponse response = get(
                url("/openai/threads/" + threadId + "/messages"),
                AzureMessageListResponse.class
        );

        if (response == null ||
                response.data() == null ||
                response.data().isEmpty()) {

            throw new AzureAssistantException(
                    "No messages returned from Azure"
            );
        }

        AzureMessageData latestMessage =
                response.data().getFirst();

        if (latestMessage.content() == null ||
                latestMessage.content().isEmpty()) {

            throw new AzureAssistantException(
                    "Azure message content missing"
            );
        }

        AzureMessageContent content =
                latestMessage.content().getFirst();

        if (content.text() == null ||
                content.text().value() == null) {

            throw new AzureAssistantException(
                    "Azure message text missing"
            );
        }

        return content.text().value();
    }

    // =========================
    // GET ASSISTANTS
    // =========================

    public List<AzureAssistantData> getAssistants() {

        log.info("Fetching Azure assistants");

        AzureAssistantListResponse response = get(
                url("/openai/assistants"),
                AzureAssistantListResponse.class
        );

        if (response == null ||
                response.data() == null) {

            throw new AzureAssistantException(
                    "Failed to fetch assistants"
            );
        }

        return response.data();
    }

    // =========================
    // GET CHAT HISTORY
    // =========================

    public List<ChatHistoryMessage> getThreadMessages(
            String threadId
    ) {

        AzureMessageListResponse response = get(
                url("/openai/threads/" + threadId + "/messages"),
                AzureMessageListResponse.class
        );

        if (response == null ||
                response.data() == null) {

            throw new AzureAssistantException(
                    "Failed to fetch thread messages"
            );
        }

        return response.data()
                .stream()
                .filter(message ->

                        message.content() != null &&
                                !message.content().isEmpty()

                )
                .map(message -> {

                    AzureMessageContent content =
                            message.content().getFirst();

                    String text = "";

                    if (content.text() != null) {

                        text =
                                content.text().value();
                    }

                    return new ChatHistoryMessage(
                            message.role(),
                            text
                    );
                })
                .toList()
                .reversed();
    }

    // =========================
// DELETE THREAD
// =========================

    public void deleteThread(String threadId) {

        log.info("Deleting Azure thread {}", threadId);

        delete(url("/openai/threads/" + threadId));
    }

    // =========================
    // GENERIC GET
    // =========================

    private <T> T get(
            String url,
            Class<T> responseType
    ) {

        try {

            ResponseEntity<T> response =
                    restTemplate.exchange(

                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            responseType
                    );

            return response.getBody();

        } catch (HttpClientErrorException ex) {

            log.error(
                    "Azure GET failed: {} | {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );

            throw new AzureAssistantException(
                    "Azure GET request failed: " +
                            ex.getStatusCode(),
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Unexpected Azure GET error",
                    ex
            );

            throw new AzureAssistantException(
                    "Unexpected Azure GET error",
                    ex
            );
        }
    }

    // =========================
    // GENERIC POST
    // =========================

    private <T> T post(
            String url,
            Object body,
            Class<T> responseType
    ) {

        try {

            HttpEntity<?> entity =
                    new HttpEntity<>(
                            body,
                            headers()
                    );

            ResponseEntity<T> response =
                    restTemplate.exchange(

                            url,
                            HttpMethod.POST,
                            entity,
                            responseType
                    );

            return response.getBody();

        } catch (HttpClientErrorException ex) {

            log.error(
                    "Azure POST failed: {} | {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );

            throw new AzureAssistantException(
                    "Azure POST request failed: " +
                            ex.getStatusCode(),
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Unexpected Azure POST error",
                    ex
            );

            throw new AzureAssistantException(
                    "Unexpected Azure POST error",
                    ex
            );
        }
    }

    // =========================
    // GENERIC PATCH
    // =========================

    private void patch(
            String url,
            Object body
    ) {
        log.info("PATCH {}", url);

        try {

            HttpEntity<?> entity =
                    new HttpEntity<>(
                            body,
                            headers()
                    );

            restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    entity,
                    Void.class
            );

        } catch (HttpClientErrorException ex) {

            log.error(
                    "Azure PATCH failed: {} | {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );

            throw new AzureAssistantException(
                    "Azure PATCH request failed",
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Unexpected Azure PATCH error",
                    ex
            );

            throw new AzureAssistantException(
                    "Unexpected Azure PATCH error",
                    ex
            );
        }
    }

    // =========================
    // GENERIC DELETE
    // =========================

    private void delete(String url) {

        try {

            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers()),
                    Void.class
            );

        } catch (HttpClientErrorException ex) {

            log.error(
                    "Azure DELETE failed: {} | {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );

            throw new AzureAssistantException(
                    "Azure DELETE request failed: " +
                            ex.getStatusCode(),
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Unexpected Azure DELETE error",
                    ex
            );

            throw new AzureAssistantException(
                    "Unexpected Azure DELETE error",
                    ex
            );
        }
    }

    // =========================
    // SLEEP
    // =========================

    private void sleep(long ms) {

        try {

            Thread.sleep(ms);

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            throw new AzureAssistantException(
                    "Polling interrupted",
                    ex
            );
        }
    }
}