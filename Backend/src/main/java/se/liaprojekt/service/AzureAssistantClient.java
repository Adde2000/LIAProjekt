package se.liaprojekt.service;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AzureAssistantClient {

    private final RestTemplate restTemplate;
    private final TokenCredential credential;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-version}")
    private String apiVersion;

    // ---------------- TOKEN ----------------

    private String getToken() {
        return credential.getToken(
                new TokenRequestContext()
                        .addScopes("https://cognitiveservices.azure.com/.default")
        ).block().getToken();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ---------------- THREAD ----------------

    public String createThread() {

        String url = endpoint + "/openai/threads?api-version=" + apiVersion;

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(headers()),
                Map.class
        );

        return (String) response.getBody().get("id");
    }

    // ---------------- MESSAGE ----------------

    public void addMessage(String threadId, String message) {

        String url = endpoint + "/openai/threads/" + threadId + "/messages?api-version=" + apiVersion;

        Map<String, Object> body = Map.of(
                "role", "user",
                "content", message
        );

        restTemplate.postForEntity(
                url,
                new HttpEntity<>(body, headers()),
                Map.class
        );
    }

    // ---------------- RUN ----------------

    public String createRun(String threadId, String assistantId) {

        String url = endpoint + "/openai/threads/" + threadId + "/runs?api-version=" + apiVersion;

        Map<String, Object> body = Map.of(
                "assistant_id", assistantId
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(body, headers()),
                Map.class
        );

        return (String) response.getBody().get("id");
    }

    // ---------------- WAIT ----------------

    public String waitForCompletion(String threadId, String runId) {

        int attempts = 0;

        while (attempts < 30) {

            String status = getRunStatus(threadId, runId);

            if ("completed".equals(status)) {
                return getLatestMessage(threadId);
            }

            if (List.of("failed", "expired", "cancelled").contains(status)) {
                throw new RuntimeException("Run failed: " + status);
            }

            sleep(1000);
            attempts++;
        }

        throw new RuntimeException("Run timeout");
    }

    // ---------------- STATUS ----------------

    public String getRunStatus(String threadId, String runId) {

        String url = endpoint + "/openai/threads/" + threadId + "/runs/" + runId + "?api-version=" + apiVersion;

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class
        );

        return (String) response.getBody().get("status");
    }

    // ---------------- RESPONSE ----------------

    public String getLatestMessage(String threadId) {

        String url = endpoint + "/openai/threads/" + threadId + "/messages?api-version=" + apiVersion;

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class
        );

        List<Map<String, Object>> data =
                (List<Map<String, Object>>) response.getBody().get("data");

        Map<String, Object> latest = data.get(0);

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) latest.get("content");

        Map<String, Object> text =
                (Map<String, Object>) content.get(0).get("text");

        return (String) text.get("value");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}