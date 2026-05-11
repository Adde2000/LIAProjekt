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

/**
 * Handles Azure OpenAI Assistants API (Threads + Runs + Polling)
 */
@Service
@RequiredArgsConstructor
public class AzureAssistantClient {

    private final TokenCredential credential;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-version}")
    private String apiVersion;

    @Value("${azure.openai.assistant-id}")
    private String assistantId;

    // ---------------- AUTH ----------------

    private String getToken() {
        return credential.getToken(
                new TokenRequestContext()
                        .addScopes("https://cognitiveservices.azure.com/.default")
        ).block().getToken();
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(getToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ---------------- THREAD ----------------

    public String createThread() {

        String url = endpoint + "/openai/threads?api-version=" + apiVersion;

        ResponseEntity<Map> res =
                restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity<>(headers()), Map.class);

        return (String) res.getBody().get("id");
    }

    // ---------------- MESSAGE ----------------

    public void addMessage(String threadId, String message) {

        String url = endpoint + "/openai/threads/" + threadId + "/messages?api-version=" + apiVersion;

        Map body = Map.of(
                "role", "user",
                "content", message
        );

        restTemplate.postForEntity(url,
                new HttpEntity<>(body, headers()),
                Map.class);
    }

    // ---------------- RUN ----------------

    public String run(String threadId, String assistantId) {

        String url = endpoint +
                "/openai/threads/" + threadId +
                "/runs?api-version=" + apiVersion;

        Map body = Map.of(
                "assistant_id", assistantId
        );

        ResponseEntity<Map> res =
                restTemplate.postForEntity(url,
                        new HttpEntity<>(body, headers()),
                        Map.class);

        return (String) res.getBody().get("id");
    }

    // ---------------- POLLING ENTRY POINT ----------------

    public String runAndWaitForResponse(String threadId, String assistantId) {

        String runId = run(threadId, assistantId);

        String status = "";
        int attempts = 0;

        while (attempts < 30) {

            status = getRunStatus(threadId, runId);

            if ("completed".equals(status)) break;

            if (List.of("failed", "cancelled", "expired").contains(status)) {
                throw new RuntimeException("Run failed: " + status);
            }

            sleep(1000);
            attempts++;
        }

        if (!"completed".equals(status)) {
            throw new RuntimeException("Run timeout");
        }

        return getLatestMessage(threadId);
    }

    // ---------------- RUN STATUS ----------------

    public String getRunStatus(String threadId, String runId) {

        String url = endpoint +
                "/openai/threads/" + threadId +
                "/runs/" + runId +
                "?api-version=" + apiVersion;

        ResponseEntity<Map> res =
                restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(headers()), Map.class);

        return (String) res.getBody().get("status");
    }

    // ---------------- RESPONSE ----------------

    public String getLatestMessage(String threadId) {

        String url = endpoint +
                "/openai/threads/" + threadId +
                "/messages?api-version=" + apiVersion;

        ResponseEntity<Map> res =
                restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(headers()), Map.class);

        List<Map<String, Object>> data =
                (List<Map<String, Object>>) res.getBody().get("data");

        Map<String, Object> latest = data.get(0);

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) latest.get("content");

        return content.get(0).get("text").toString();
    }

    // ---------------- UTIL ----------------

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}