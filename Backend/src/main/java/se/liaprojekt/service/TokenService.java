package se.liaprojekt.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class TokenService {

    @Value("${spring.cloud.azure.tenant-id}")
    private String tenantId;

    @Value("${spring.cloud.azure.credential.client-id}")
    private String clientId;

    @Value("${spring.cloud.azure.credential.client-secret}")
    private String clientSecret;

    private final WebClient webClient = WebClient.builder().build();

    public String getGraphToken() {

        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        JsonNode response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(
                        "client_id=" + clientId +
                                "&scope=https://graph.microsoft.com/.default" +
                                "&client_secret=" + clientSecret +
                                "&grant_type=client_credentials"
                )
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null)
        {
            return "";
        }
        return response.get("access_token").asText();
    }
}