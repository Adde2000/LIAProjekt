package se.liaprojekt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GraphService {

    private final WebClient webClient;
    private final TokenService tokenService;

    @Value("${graph.base-url}")
    private String graphBaseUrl;

    public GraphService(WebClient.Builder builder, TokenService tokenService) {
        this.webClient = builder.build();
        this.tokenService = tokenService;
    }

    public String getUsers() {

        String token = tokenService.getGraphToken();

        return webClient.get()
                .uri(graphBaseUrl + "/users")
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}