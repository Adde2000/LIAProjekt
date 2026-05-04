package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import se.liaprojekt.dto.GraphAPIResponse;
import se.liaprojekt.dto.UserResponse;

import java.util.List;

@Service
public class GraphService {

    private final WebClient webClient;
    private final TokenService tokenService;
    private final RestTemplate restTemplate;

    @Value("${graph.base-url}")
    private String graphBaseUrl;

    public GraphService(WebClient.Builder builder, TokenService tokenService) {
        this.webClient = builder.build();
        this.tokenService = tokenService;
        this.restTemplate = new RestTemplate();
    }

    public List<UserResponse> getUsers() {

        String token = tokenService.getAccessToken(restTemplate);

        HttpHeaders headers = new HttpHeaders();
        HttpEntity entity = new HttpEntity(headers);
        headers.setBearerAuth(token);
        ResponseEntity<GraphAPIResponse> response = restTemplate.exchange(
                graphBaseUrl + "/users",
                HttpMethod.GET,
                entity,
                GraphAPIResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody().value();
        } else {
            //TODO throw appropriate exception when request fail
        }

        return null;
//        return webClient.get()
//                .uri(graphBaseUrl + "/users")
//                .headers(h -> h.setBearerAuth(token))
//                .retrieve()
//                .bodyToMono(String.class)
//                .block();
    }
}